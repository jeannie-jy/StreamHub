package com.streamhub.live;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ConsistencyReconciliationJob {
    private static final Logger log = LoggerFactory.getLogger(ConsistencyReconciliationJob.class);
    private static final RedisScript<Long> RELEASE_LOCK_SCRIPT = RedisScript.of(
            "if redis.call('GET', KEYS[1]) == ARGV[1] "
                    + "then return redis.call('DEL', KEYS[1]) end return 0",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final GiftService giftService;
    private final ActivityService activityService;
    private final boolean enabled;
    private final String lockKey;
    private final long pendingAgeMs;
    private final int batchSize;

    public ConsistencyReconciliationJob(
            StringRedisTemplate stringRedisTemplate,
            GiftService giftService,
            ActivityService activityService,
            @Value("${streamhub.reconciliation.enabled:true}") boolean enabled,
            @Value("${streamhub.reconciliation.lock-key:streamhub:lock:reconciliation}") String lockKey,
            @Value("${streamhub.reconciliation.pending-age-ms:30000}") long pendingAgeMs,
            @Value("${streamhub.reconciliation.batch-size:100}") int batchSize) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.giftService = giftService;
        this.activityService = activityService;
        this.enabled = enabled;
        this.lockKey = lockKey;
        this.pendingAgeMs = Math.max(0, pendingAgeMs);
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${streamhub.reconciliation.fixed-delay-ms:60000}")
    public void reconcile() {
        if (!enabled) {
            return;
        }

        String lockToken = UUID.randomUUID().toString();
        try {
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                    lockKey,
                    lockToken,
                    Duration.ofSeconds(45));
            if (!Boolean.TRUE.equals(acquired)) {
                return;
            }

            try {
                Instant createdBefore = Instant.now().minusMillis(pendingAgeMs);
                int retriedGiftOrders = giftService.retryPending(createdBefore, batchSize);
                int retriedActivityOrders = activityService.retryPending(createdBefore, batchSize);
                int rebuiltRanks = giftService.rebuildRanks();
                int repairedActivities = activityService.reconcileActiveInventory();
                log.info(
                        "一致性对账完成 retriedGiftOrders={} retriedActivityOrders={} "
                                + "rebuiltRanks={} repairedActivities={}",
                        retriedGiftOrders,
                        retriedActivityOrders,
                        rebuiltRanks,
                        repairedActivities);
            } finally {
                stringRedisTemplate.execute(
                        RELEASE_LOCK_SCRIPT,
                        List.of(lockKey),
                        lockToken);
            }
        } catch (RuntimeException exception) {
            log.warn("一致性对账任务执行失败", exception);
        }
    }
}
