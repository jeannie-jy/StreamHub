package com.streamhub.live;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MqMetrics {
    private final MeterRegistry meterRegistry;
    private final MqOutboxRepository outboxRepository;
    private final MqDeadLetterRepository deadLetterRepository;
    private final GiftOrderRepository giftOrderRepository;
    private final ActivityOrderRepository activityOrderRepository;
    private final AtomicLong outboxPending = new AtomicLong();
    private final AtomicLong outboxFailed = new AtomicLong();
    private final AtomicLong outboxOldestAgeSeconds = new AtomicLong();
    private final AtomicLong deadLettersNew = new AtomicLong();
    private final AtomicLong giftOrdersPending = new AtomicLong();
    private final AtomicLong activityOrdersPending = new AtomicLong();
    private final Map<String, AtomicLong> consumerLag = new ConcurrentHashMap<>();

    public MqMetrics(
            MeterRegistry meterRegistry,
            MqOutboxRepository outboxRepository,
            MqDeadLetterRepository deadLetterRepository,
            GiftOrderRepository giftOrderRepository,
            ActivityOrderRepository activityOrderRepository,
            RocketMqProperties properties) {
        this.meterRegistry = meterRegistry;
        this.outboxRepository = outboxRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.giftOrderRepository = giftOrderRepository;
        this.activityOrderRepository = activityOrderRepository;
        registerGauge("streamhub.mq.outbox.pending", outboxPending);
        registerGauge("streamhub.mq.outbox.failed", outboxFailed);
        registerGauge("streamhub.mq.outbox.oldest.age.seconds", outboxOldestAgeSeconds);
        registerGauge("streamhub.mq.deadletter.new", deadLettersNew);
        Gauge.builder("streamhub.mq.order.pending", giftOrdersPending, AtomicLong::get)
                .tag("type", "gift")
                .register(meterRegistry);
        Gauge.builder("streamhub.mq.order.pending", activityOrdersPending, AtomicLong::get)
                .tag("type", "activity")
                .register(meterRegistry);
        registerConsumerLag(properties.getGiftConsumerGroup());
        registerConsumerLag(properties.getActivityConsumerGroup());
    }

    public void recordConsume(String consumerGroup, String result, int reconsumeTimes) {
        meterRegistry.counter(
                "streamhub.mq.consume",
                "consumer_group", consumerGroup,
                "result", result).increment();
        if (reconsumeTimes > 0) {
            meterRegistry.counter(
                    "streamhub.mq.retry.observed",
                    "consumer_group", consumerGroup).increment();
        }
    }

    public void recordOutboxSend(String topic, String result) {
        meterRegistry.counter("streamhub.mq.outbox.send", "topic", topic, "result", result).increment();
    }

    public void recordDeadLetterCaptured() {
        meterRegistry.counter("streamhub.mq.deadletter.captured").increment();
    }

    public void updateConsumerLag(String consumerGroup, long lag) {
        AtomicLong gauge = consumerLag.get(consumerGroup);
        if (gauge != null) {
            gauge.set(Math.max(0, lag));
        }
    }

    @Scheduled(fixedDelayString = "${streamhub.rocketmq.metrics-refresh-ms:15000}")
    public void refreshPersistenceBacklog() {
        try {
            outboxPending.set(outboxRepository.countPending());
            outboxFailed.set(outboxRepository.countFailed());
            outboxOldestAgeSeconds.set(outboxRepository.oldestPendingAgeSeconds());
            deadLetterRepository.reconcileReplayed();
            deadLettersNew.set(deadLetterRepository.countNew());
            giftOrdersPending.set(giftOrderRepository.countPending());
            activityOrdersPending.set(activityOrderRepository.countPending());
        } catch (RuntimeException ignored) {
            // Keep the last good sample; database health is reported separately by Actuator.
        }
    }

    private void registerGauge(String name, AtomicLong value) {
        Gauge.builder(name, value, AtomicLong::get).register(meterRegistry);
    }

    private void registerConsumerLag(String consumerGroup) {
        AtomicLong value = new AtomicLong();
        consumerLag.put(consumerGroup, value);
        Gauge.builder("streamhub.mq.consumer.lag", value, AtomicLong::get)
                .tag("consumer_group", consumerGroup)
                .register(meterRegistry);
    }
}
