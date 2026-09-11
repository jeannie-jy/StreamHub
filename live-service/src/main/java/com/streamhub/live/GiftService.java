package com.streamhub.live;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;

import com.streamhub.common.api.BusinessException;
import com.streamhub.common.api.ErrorCode;
import com.streamhub.common.api.PageResult;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class GiftService {
    private static final Logger log = LoggerFactory.getLogger(GiftService.class);

    private final GiftCatalogRepository giftCatalogRepository;
    private final GiftOrderRepository giftOrderRepository;
    private final WalletRepository walletRepository;
    private final LiveRoomRepository liveRoomRepository;
    private final DefaultMQProducer giftOrderProducer;
    private final RocketMqProperties rocketMqProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final RoomWebSocketHandler roomWebSocketHandler;

    public GiftService(
            GiftCatalogRepository giftCatalogRepository,
            GiftOrderRepository giftOrderRepository,
            WalletRepository walletRepository,
            LiveRoomRepository liveRoomRepository,
            @Qualifier("giftOrderProducer") DefaultMQProducer giftOrderProducer,
            RocketMqProperties rocketMqProperties,
            StringRedisTemplate stringRedisTemplate,
            RoomWebSocketHandler roomWebSocketHandler) {
        this.giftCatalogRepository = giftCatalogRepository;
        this.giftOrderRepository = giftOrderRepository;
        this.walletRepository = walletRepository;
        this.liveRoomRepository = liveRoomRepository;
        this.giftOrderProducer = giftOrderProducer;
        this.rocketMqProperties = rocketMqProperties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.roomWebSocketHandler = roomWebSocketHandler;
    }

    public List<GiftCatalog> listGifts() {
        return giftCatalogRepository.findActive();
    }

    public WalletView wallet(long userId) {
        return new WalletView(userId, walletRepository.balance(userId));
    }

    @Transactional
    public WalletView recharge(long userId, String bizNo, long amount) {
        walletRepository.ensureAccount(userId);
        long current = walletRepository.balanceForUpdate(userId);
        var existing = walletRepository.findLedger(bizNo);
        if (existing.isPresent()) {
            if (existing.get().userId() != userId || existing.get().changeAmount() != amount) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "充值业务号已被其他请求使用");
            }
            return wallet(userId);
        }

        long next = addExact(current, amount, "余额超出范围");
        walletRepository.updateBalance(userId, next);
        walletRepository.insertLedger(bizNo, userId, amount, next, "RECHARGE", bizNo);
        return new WalletView(userId, next);
    }

    public GiftOrderView placeOrder(long roomId, long senderId, String giftCode, int quantity, String clientOrderNo) {
        LiveRoom room = liveRoomRepository.findById(roomId).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "直播间不存在"));
        if (!"LIVE".equals(room.status())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "直播间当前未开播");
        }
        if (room.anchorId() == senderId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "主播不能给自己送礼");
        }
        GiftCatalog gift = giftCatalogRepository.findActiveByCode(giftCode.trim()).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "礼物不存在或已下架"));
        long totalAmount = multiplyExact(gift.price(), quantity, "礼物金额超出范围");
        String orderNo = clientOrderNo.trim();
        boolean created = giftOrderRepository.createPending(
                orderNo, roomId, senderId, room.anchorId(), gift.id(), quantity, totalAmount);
        GiftOrder order = giftOrderRepository.findByOrderNo(orderNo).orElseThrow(
                () -> new BusinessException(ErrorCode.INTERNAL_ERROR, "礼物订单创建失败"));
        if (!created) {
            if (order.roomId() != roomId || order.senderId() != senderId || order.giftId() != gift.id()
                    || order.quantity() != quantity) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "clientOrderNo 已被其他订单使用");
            }
            if ("PENDING".equals(order.status())) {
                sendOrderMessage(orderNo);
            }
            return GiftOrderView.from(order);
        }

        try {
            sendOrderMessage(orderNo);
        } catch (Exception exception) {
            giftOrderRepository.markFailed(orderNo, "消息投递失败");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "礼物订单投递失败");
        }
        return GiftOrderView.from(order);
    }

    private void sendOrderMessage(String orderNo) {
        try {
            Message message = new Message(
                    rocketMqProperties.getGiftTopic(),
                    orderNo.getBytes(StandardCharsets.UTF_8));
            message.setKeys(orderNo);
            giftOrderProducer.send(message);
        } catch (Exception exception) {
            throw new IllegalStateException("礼物订单消息投递失败", exception);
        }
    }

    public GiftOrderView findOrder(String orderNo) {
        return giftOrderRepository.findByOrderNo(orderNo)
                .map(GiftOrderView::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "礼物订单不存在"));
    }

    public PageResult<GiftOrderView> ordersForUser(long userId, int page, int pageSize) {
        PageResult<GiftOrder> result = giftOrderRepository.findBySenderId(userId, page, pageSize);
        return new PageResult<>(result.items().stream().map(GiftOrderView::from).toList(), result.page(), result.pageSize(), result.total(), result.hasNext());
    }

    public List<GiftRankEntry> contributorRank(long roomId, int limit) {
        return rank(roomId, limit, "contributors");
    }

    public List<GiftRankEntry> incomeRank(long roomId, int limit) {
        return rank(roomId, limit, "income");
    }

    public int retryPending(Instant createdBefore, int limit) {
        int retried = 0;
        for (GiftOrder order : giftOrderRepository.findPendingOlderThan(createdBefore, limit)) {
            try {
                sendOrderMessage(order.orderNo());
                retried++;
            } catch (RuntimeException exception) {
                log.warn("补偿礼物订单消息失败 orderNo={}", order.orderNo(), exception);
            }
        }
        return retried;
    }

    public int rebuildRanks() {
        int rebuilt = 0;
        for (Long roomId : giftOrderRepository.findRankRoomIds()) {
            stringRedisTemplate.delete(rankKey(roomId, "contributors"));
            stringRedisTemplate.delete(rankKey(roomId, "income"));
            for (GiftRankEntry entry : giftOrderRepository.contributorRank(roomId)) {
                stringRedisTemplate.opsForZSet().add(
                        rankKey(roomId, "contributors"),
                        String.valueOf(entry.userId()),
                        entry.amount());
                rebuilt++;
            }
            for (GiftRankEntry entry : giftOrderRepository.incomeRank(roomId)) {
                stringRedisTemplate.opsForZSet().add(
                        rankKey(roomId, "income"),
                        String.valueOf(entry.userId()),
                        entry.amount());
                rebuilt++;
            }
        }
        return rebuilt;
    }

    private List<GiftRankEntry> rank(long roomId, int limit, String rankType) {
        String key = rankKey(roomId, rankType);
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(key, 0, limit - 1);
        List<GiftRankEntry> result = new ArrayList<>();
        if (tuples == null) {
            return result;
        }
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple.getValue() != null && tuple.getScore() != null) {
                result.add(new GiftRankEntry(rank++, Long.parseLong(tuple.getValue()), tuple.getScore().longValue()));
            }
        }
        return result;
    }

    @Transactional
    public GiftOrderProcessingResult processOrder(String orderNo) {
        GiftOrder order = giftOrderRepository.findByOrderNoForUpdate(orderNo).orElseThrow(
                () -> new IllegalStateException("找不到待处理的礼物订单: " + orderNo));
        if (!"PENDING".equals(order.status())) {
            return new GiftOrderProcessingResult(GiftOrderView.from(order), false);
        }

        walletRepository.ensureAccount(order.senderId());
        walletRepository.ensureAccount(order.anchorId());
        long firstUserId = Math.min(order.senderId(), order.anchorId());
        long secondUserId = Math.max(order.senderId(), order.anchorId());
        long firstBalance = walletRepository.balanceForUpdate(firstUserId);
        long secondBalance = firstUserId == secondUserId
                ? firstBalance
                : walletRepository.balanceForUpdate(secondUserId);
        long senderBalance = order.senderId() == firstUserId ? firstBalance : secondBalance;
        if (senderBalance < order.totalAmount()) {
            giftOrderRepository.markFailed(orderNo, "虚拟金币余额不足");
            GiftOrder failed = giftOrderRepository.findByOrderNo(orderNo).orElseThrow();
            return new GiftOrderProcessingResult(GiftOrderView.from(failed), false);
        }

        long nextSenderBalance = senderBalance - order.totalAmount();
        long anchorBalance = order.anchorId() == firstUserId ? firstBalance : secondBalance;
        long nextAnchorBalance = addExact(anchorBalance, order.totalAmount(), "主播收益超出范围");
        if (order.senderId() == order.anchorId()) {
            walletRepository.updateBalance(order.senderId(), anchorBalance);
        } else {
            walletRepository.updateBalance(order.senderId(), nextSenderBalance);
            walletRepository.updateBalance(order.anchorId(), nextAnchorBalance);
        }
        walletRepository.insertLedger(
                "gift:" + order.orderNo(),
                order.senderId(),
                -order.totalAmount(),
                nextSenderBalance,
                "GIFT_DEBIT",
                order.orderNo());
        walletRepository.insertLedger(
                "gift-income:" + order.orderNo(),
                order.anchorId(),
                order.totalAmount(),
                nextAnchorBalance,
                "GIFT_INCOME",
                order.orderNo());
        giftOrderRepository.markSuccess(orderNo);
        GiftOrder success = giftOrderRepository.findByOrderNo(orderNo).orElseThrow();
        return new GiftOrderProcessingResult(GiftOrderView.from(success), true);
    }

    public void publishSuccess(GiftOrderView order) {
        try {
            stringRedisTemplate.opsForZSet().incrementScore(
                    rankKey(order.roomId(), "contributors"),
                    String.valueOf(order.senderId()),
                    order.totalAmount());
            stringRedisTemplate.opsForZSet().incrementScore(
                    rankKey(order.roomId(), "income"),
                    String.valueOf(order.anchorId()),
                    order.totalAmount());
            roomWebSocketHandler.broadcastEvent(order.roomId(), Map.of(
                    "type", "GIFT",
                    "orderNo", order.orderNo(),
                    "roomId", order.roomId(),
                    "senderId", order.senderId(),
                    "anchorId", order.anchorId(),
                    "giftCode", order.giftCode(),
                    "quantity", order.quantity(),
                    "totalAmount", order.totalAmount(),
                    "createdAt", order.createdAt()));
        } catch (RuntimeException exception) {
            // 业务扣款已经提交，实时榜和动画失败时由后续补偿任务修复，不能让 MQ 无限重试。
        }
    }

    private String rankKey(long roomId, String rankType) {
        return "live:room:" + roomId + ":rank:" + rankType;
    }

    private long addExact(long left, long right, String message) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
        }
    }

    private long multiplyExact(long left, long right, String message) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
        }
    }

    public record GiftOrderProcessingResult(GiftOrderView order, boolean newlySucceeded) {
    }
}
