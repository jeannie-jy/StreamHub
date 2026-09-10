package com.streamhub.live;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.streamhub.common.api.BusinessException;
import com.streamhub.common.api.ErrorCode;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.script.RedisScript;

@Service
public class ActivityService {
    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);
    private static final long STOCK_NOT_ENOUGH = -1L;
    private static final long USER_ALREADY_JOINED = -2L;
    private static final RedisScript<Long> RESERVE_SCRIPT = RedisScript.of(
            "local joined = redis.call('SISMEMBER', KEYS[2], ARGV[1]) "
                    + "if joined == 1 then return -2 end "
                    + "local stock = tonumber(redis.call('GET', KEYS[1]) or '-1') "
                    + "if stock <= 0 then return -1 end "
                    + "redis.call('DECR', KEYS[1]) "
                    + "redis.call('SADD', KEYS[2], ARGV[1]) "
                    + "return stock - 1",
            Long.class);
    private static final RedisScript<Long> ROLLBACK_SCRIPT = RedisScript.of(
            "local removed = redis.call('SREM', KEYS[2], ARGV[1]) "
                    + "if removed == 1 then return redis.call('INCR', KEYS[1]) end "
                    + "return 0",
            Long.class);

    private final ActivityRepository activityRepository;
    private final ActivityOrderRepository activityOrderRepository;
    private final LiveRoomRepository liveRoomRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMqProperties rocketMqProperties;
    private final DefaultMQProducer activityOrderProducer;
    private final RoomWebSocketHandler roomWebSocketHandler;

    public ActivityService(
            ActivityRepository activityRepository,
            ActivityOrderRepository activityOrderRepository,
            LiveRoomRepository liveRoomRepository,
            StringRedisTemplate stringRedisTemplate,
            RocketMqProperties rocketMqProperties,
            @Qualifier("activityOrderProducer") DefaultMQProducer activityOrderProducer,
            RoomWebSocketHandler roomWebSocketHandler) {
        this.activityRepository = activityRepository;
        this.activityOrderRepository = activityOrderRepository;
        this.liveRoomRepository = liveRoomRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.rocketMqProperties = rocketMqProperties;
        this.activityOrderProducer = activityOrderProducer;
        this.roomWebSocketHandler = roomWebSocketHandler;
    }

    public ActivityView create(
            long roomId,
            long userId,
            String name,
            int stock,
            long unitPrice,
            Instant startsAt,
            Instant endsAt) {
        LiveRoom room = liveRoomRepository.findById(roomId).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "直播间不存在"));
        if (room.anchorId() != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有主播可以创建本直播间活动");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "活动结束时间必须晚于开始时间");
        }
        long activityId = activityRepository.create(roomId, name.trim(), stock, unitPrice, startsAt, endsAt);
        return view(activityRepository.findById(activityId).orElseThrow());
    }

    public ActivityView start(long activityId, long userId) {
        Activity activity = findActivity(activityId);
        LiveRoom room = liveRoomRepository.findById(activity.roomId()).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "直播间不存在"));
        if (room.anchorId() != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有主播可以启动本直播间活动");
        }
        stringRedisTemplate.opsForValue().set(stockKey(activityId), String.valueOf(activity.stock()));
        stringRedisTemplate.delete(userKey(activityId));
        if (!activityRepository.activate(activityId)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "活动当前不能启动");
        }
        return view(findActivity(activityId));
    }

    public ActivityView get(long activityId) {
        return view(findActivity(activityId));
    }

    public ActivityOrderView seckill(long activityId, long userId, String clientOrderNo) {
        Activity activity = findActivity(activityId);
        if (!"ACTIVE".equals(activity.status())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "活动当前未开始");
        }
        Instant now = Instant.now();
        if (now.isBefore(activity.startsAt()) || !now.isBefore(activity.endsAt())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "活动不在进行时间内");
        }
        var existingOrder = activityOrderRepository.findByActivityAndUser(activityId, userId);
        if (existingOrder.isPresent()) {
            return ActivityOrderView.from(existingOrder.get());
        }

        Long remaining = stringRedisTemplate.execute(
                RESERVE_SCRIPT,
                List.of(stockKey(activityId), userKey(activityId)),
                String.valueOf(userId));
        if (remaining == null || remaining == USER_ALREADY_JOINED) {
            return activityOrderRepository.findByActivityAndUser(activityId, userId)
                    .map(ActivityOrderView::from)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ARGUMENT, "用户已经参与过活动"));
        }
        if (remaining == STOCK_NOT_ENOUGH) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "活动库存不足");
        }

        String orderNo = clientOrderNo.trim();
        boolean created = activityOrderRepository.createPending(orderNo, activityId, userId);
        if (!created) {
            rollbackReservation(activityId, userId);
            return activityOrderRepository.findByOrderNo(orderNo)
                    .or(() -> activityOrderRepository.findByActivityAndUser(activityId, userId))
                    .map(ActivityOrderView::from)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ARGUMENT, "订单号已被使用"));
        }

        try {
            sendMessage("CREATE", orderNo, 0);
            sendMessage("CLOSE", orderNo, rocketMqProperties.getTimeoutDelayLevel());
        } catch (Exception exception) {
            rollbackReservation(activityId, userId);
            activityOrderRepository.closeIfPending(orderNo);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "秒杀订单投递失败");
        }
        return activityOrderRepository.findByOrderNo(orderNo)
                .map(ActivityOrderView::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "秒杀订单创建失败"));
    }

    public ActivityOrderView findOrder(String orderNo) {
        return activityOrderRepository.findByOrderNo(orderNo)
                .map(ActivityOrderView::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "活动订单不存在"));
    }

    public int retryPending(Instant createdBefore, int limit) {
        int retried = 0;
        for (ActivityOrder order : activityOrderRepository.findPendingOlderThan(createdBefore, limit)) {
            try {
                sendMessage("CREATE", order.orderNo(), 0);
                sendMessage("CLOSE", order.orderNo(), rocketMqProperties.getTimeoutDelayLevel());
                retried++;
            } catch (RuntimeException exception) {
                log.warn("补偿活动订单消息失败 orderNo={}", order.orderNo(), exception);
            } catch (Exception exception) {
                log.warn("补偿活动订单消息失败 orderNo={}", order.orderNo(), exception);
            }
        }
        return retried;
    }

    public int reconcileActiveInventory() {
        int repaired = 0;
        for (Activity activity : activityRepository.findActive()) {
            List<Long> reservedUserIds = activityOrderRepository.findReservedUserIds(activity.id());
            int remainingStock = Math.max(0, activity.stock() - reservedUserIds.size());
            if (reservedUserIds.size() > activity.stock()) {
                log.warn("活动库存对账发现预占数量超过初始库存 activityId={} stock={} reserved={}",
                        activity.id(), activity.stock(), reservedUserIds.size());
            }

            String stockKey = stockKey(activity.id());
            String userKey = userKey(activity.id());
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(remainingStock));
            stringRedisTemplate.delete(userKey);
            if (!reservedUserIds.isEmpty()) {
                String[] members = reservedUserIds.stream()
                        .map(String::valueOf)
                        .toArray(String[]::new);
                stringRedisTemplate.opsForSet().add(userKey, members);
            }
            repaired++;
        }
        return repaired;
    }

    public boolean processCreate(String orderNo) {
        boolean changed = activityOrderRepository.markSuccess(orderNo);
        if (changed) {
            activityOrderRepository.findByOrderNo(orderNo).ifPresent(order ->
                    broadcastOrder(order, "SUCCESS"));
        }
        return changed;
    }

    public boolean processClose(String orderNo) {
        var order = activityOrderRepository.findByOrderNo(orderNo);
        if (order.isEmpty() || !activityOrderRepository.closeIfPending(orderNo)) {
            return false;
        }
        rollbackReservation(order.get().activityId(), order.get().userId());
        activityOrderRepository.findByOrderNo(orderNo).ifPresent(closed ->
                broadcastOrder(closed, "CLOSED"));
        return true;
    }

    private void sendMessage(String type, String orderNo, int delayLevel) throws Exception {
        Message message = new Message(
                rocketMqProperties.getActivityTopic(),
                (type + "|" + orderNo).getBytes(StandardCharsets.UTF_8));
        message.setKeys(orderNo);
        if (delayLevel > 0) {
            message.setDelayTimeLevel(delayLevel);
        }
        activityOrderProducer.send(message);
    }

    private void rollbackReservation(long activityId, long userId) {
        stringRedisTemplate.execute(
                ROLLBACK_SCRIPT,
                List.of(stockKey(activityId), userKey(activityId)),
                String.valueOf(userId));
    }

    private void broadcastOrder(ActivityOrder order, String status) {
        try {
            roomWebSocketHandler.broadcastEvent(
                    activityRepository.findById(order.activityId()).orElseThrow().roomId(),
                    Map.of(
                            "type", "ACTIVITY_ORDER",
                            "orderNo", order.orderNo(),
                            "activityId", order.activityId(),
                            "userId", order.userId(),
                            "status", status));
        } catch (RuntimeException exception) {
            // 订单状态已经持久化，实时通知失败由后续补偿处理。
        }
    }

    private Activity findActivity(long activityId) {
        return activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "活动不存在"));
    }

    private ActivityView view(Activity activity) {
        String remainingValue = stringRedisTemplate.opsForValue().get(stockKey(activity.id()));
        int remainingStock = remainingValue == null ? activity.stock() : Integer.parseInt(remainingValue);
        return new ActivityView(
                activity.id(),
                activity.roomId(),
                activity.name(),
                activity.stock(),
                remainingStock,
                activity.unitPrice(),
                activity.status(),
                activity.startsAt(),
                activity.endsAt(),
                activity.createdAt(),
                activity.updatedAt());
    }

    private String stockKey(long activityId) {
        return "activity:" + activityId + ":stock";
    }

    private String userKey(long activityId) {
        return "activity:" + activityId + ":users";
    }
}
