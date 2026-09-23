package com.streamhub.live;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MqOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(MqOutboxDispatcher.class);

    private final MqOutboxRepository outboxRepository;
    private final DefaultMQProducer giftOrderProducer;
    private final DefaultMQProducer activityOrderProducer;
    private final RocketMqProperties properties;
    private final MqMetrics metrics;
    private final MqDeadLetterRepository deadLetterRepository;

    public MqOutboxDispatcher(
            MqOutboxRepository outboxRepository,
            @Qualifier("giftOrderProducer") DefaultMQProducer giftOrderProducer,
            @Qualifier("activityOrderProducer") DefaultMQProducer activityOrderProducer,
            RocketMqProperties properties,
            MqMetrics metrics,
            MqDeadLetterRepository deadLetterRepository) {
        this.outboxRepository = outboxRepository;
        this.giftOrderProducer = giftOrderProducer;
        this.activityOrderProducer = activityOrderProducer;
        this.properties = properties;
        this.metrics = metrics;
        this.deadLetterRepository = deadLetterRepository;
    }

    @Scheduled(fixedDelayString = "${streamhub.rocketmq.outbox-fixed-delay-ms:500}")
    public void dispatch() {
        if (!properties.isOutboxEnabled()) {
            return;
        }
        outboxRepository.recoverStaleSending(Instant.now().minusSeconds(300));
        int maxAttempts = Math.max(1, properties.getOutboxMaxAttempts());
        for (MqOutboxRepository.OutboxMessage outbox :
                outboxRepository.findDispatchable(maxAttempts, properties.getOutboxBatchSize())) {
            if (!outboxRepository.claim(outbox.id(), maxAttempts)) {
                continue;
            }
            int attempt = outbox.attempts() + 1;
            try {
                send(outbox);
                outboxRepository.markSent(outbox.id());
                try {
                    deadLetterRepository.markReplayedByOutbox(outbox.id());
                } catch (RuntimeException exception) {
                    log.warn("Outbox 已发送，但更新死信重放状态失败 outboxId={}", outbox.id(), exception);
                }
                metrics.recordOutboxSend(outbox.topic(), "success");
            } catch (Exception exception) {
                long delayMs = retryDelayMs(attempt);
                outboxRepository.markFailure(
                        outbox.id(),
                        attempt,
                        maxAttempts,
                        Instant.now().plusMillis(delayMs),
                        exception.getClass().getSimpleName() + ": " + exception.getMessage());
                metrics.recordOutboxSend(outbox.topic(), attempt >= maxAttempts ? "exhausted" : "failure");
                log.warn("Outbox 消息投递失败 eventKey={} attempt={}/{}",
                        outbox.eventKey(), attempt, maxAttempts, exception);
            }
        }
    }

    private void send(MqOutboxRepository.OutboxMessage outbox) throws Exception {
        Message message = new Message(outbox.topic(), outbox.body().getBytes(StandardCharsets.UTF_8));
        if (outbox.tag() != null && !outbox.tag().isBlank()) {
            message.setTags(outbox.tag());
        }
        if (outbox.messageKey() != null && !outbox.messageKey().isBlank()) {
            message.setKeys(outbox.messageKey());
        }
        if (outbox.delayLevel() > 0) {
            message.setDelayTimeLevel(outbox.delayLevel());
        }
        producerFor(outbox.topic()).send(message);
    }

    private DefaultMQProducer producerFor(String topic) {
        if (properties.getGiftTopic().equals(topic)) {
            return giftOrderProducer;
        }
        if (properties.getActivityTopic().equals(topic)) {
            return activityOrderProducer;
        }
        throw new IllegalArgumentException("Outbox 不允许投递未知 Topic: " + topic);
    }

    private long retryDelayMs(int attempt) {
        long base = Math.max(100, properties.getOutboxBaseRetryDelayMs());
        int exponent = Math.min(10, Math.max(0, attempt - 1));
        return Math.min(300_000, base * (1L << exponent));
    }
}
