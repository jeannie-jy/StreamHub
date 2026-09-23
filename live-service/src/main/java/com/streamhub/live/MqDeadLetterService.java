package com.streamhub.live;

import java.nio.charset.StandardCharsets;

import com.streamhub.common.api.BusinessException;
import com.streamhub.common.api.ErrorCode;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MqDeadLetterService {
    private static final String DLQ_PREFIX = "%DLQ%";

    private final MqDeadLetterRepository deadLetterRepository;
    private final MqOutboxRepository outboxRepository;
    private final RocketMqProperties properties;
    private final OpsAuditRepository auditRepository;

    public MqDeadLetterService(
            MqDeadLetterRepository deadLetterRepository,
            MqOutboxRepository outboxRepository,
            RocketMqProperties properties,
            OpsAuditRepository auditRepository) {
        this.deadLetterRepository = deadLetterRepository;
        this.outboxRepository = outboxRepository;
        this.properties = properties;
        this.auditRepository = auditRepository;
    }

    public void captureBrokerDeadLetter(MessageExt message) {
        String consumerGroup = message.getTopic().startsWith(DLQ_PREFIX)
                ? message.getTopic().substring(DLQ_PREFIX.length())
                : "unknown";
        capture(consumerGroup, message, "RocketMQ 重试次数耗尽");
    }

    public void capturePermanentFailure(String consumerGroup, MessageExt message, RuntimeException exception) {
        capture(consumerGroup, message, exception.getClass().getSimpleName() + ": " + exception.getMessage());
    }

    @Transactional
    public MqDeadLetterRepository.DeadLetterMessage replay(long id, long operatorId, String reason) {
        MqDeadLetterRepository.DeadLetterMessage deadLetter = deadLetterRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "死信消息不存在"));
        if (!"NEW".equals(deadLetter.status())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "只有 NEW 状态的死信可以重放");
        }
        validateReplayTopic(deadLetter.originalTopic());
        long outboxId = outboxRepository.enqueue(
                "dlq-replay:" + deadLetter.id(),
                deadLetter.originalTopic(),
                deadLetter.tag(),
                deadLetter.messageKeys(),
                deadLetter.body(),
                0);
        if (!deadLetterRepository.markReplayQueued(id, outboxId)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "死信状态已发生变化");
        }
        auditRepository.append(operatorId, "REPLAY_MQ_DEAD_LETTER", "MQ_DEAD_LETTER", String.valueOf(id), reason);
        return deadLetterRepository.findByIdForUpdate(id).orElseThrow();
    }

    @Transactional
    public MqDeadLetterRepository.DeadLetterMessage ignore(long id, long operatorId, String reason) {
        MqDeadLetterRepository.DeadLetterMessage deadLetter = deadLetterRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "死信消息不存在"));
        if (!deadLetterRepository.markIgnored(id)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "只有 NEW 状态的死信可以忽略");
        }
        auditRepository.append(operatorId, "IGNORE_MQ_DEAD_LETTER", "MQ_DEAD_LETTER", String.valueOf(id), reason);
        return deadLetterRepository.findByIdForUpdate(id).orElseThrow();
    }

    private void capture(String consumerGroup, MessageExt message, String failureReason) {
        String originalTopic = firstNonBlank(
                message.getProperty(MessageConst.PROPERTY_DLQ_ORIGIN_TOPIC),
                message.getProperty(MessageConst.PROPERTY_RETRY_TOPIC),
                message.getTopic());
        String messageId = firstNonBlank(
                message.getProperty(MessageConst.PROPERTY_DLQ_ORIGIN_MESSAGE_ID),
                message.getMsgId(),
                message.getTopic() + ":" + message.getQueueId() + ":" + message.getQueueOffset());
        deadLetterRepository.capture(
                consumerGroup,
                originalTopic,
                messageId,
                message.getKeys(),
                message.getTags(),
                new String(message.getBody(), StandardCharsets.UTF_8),
                message.getReconsumeTimes(),
                failureReason);
    }

    private void validateReplayTopic(String topic) {
        if (!properties.getGiftTopic().equals(topic) && !properties.getActivityTopic().equals(topic)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "不允许重放未知 Topic: " + topic);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }
}
