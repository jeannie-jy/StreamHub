package com.streamhub.live;

import java.util.List;

import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterMessageListener implements MessageListenerConcurrently {
    private static final Logger log = LoggerFactory.getLogger(DeadLetterMessageListener.class);

    private final MqDeadLetterService deadLetterService;
    private final MqMetrics metrics;

    public DeadLetterMessageListener(MqDeadLetterService deadLetterService, MqMetrics metrics) {
        this.deadLetterService = deadLetterService;
        this.metrics = metrics;
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(
            List<MessageExt> messages,
            ConsumeConcurrentlyContext context) {
        try {
            for (MessageExt message : messages) {
                deadLetterService.captureBrokerDeadLetter(message);
                metrics.recordDeadLetterCaptured();
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (RuntimeException exception) {
            log.error("持久化 RocketMQ 死信失败，将重试", exception);
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }
}
