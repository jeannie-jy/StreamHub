package com.streamhub.live;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GiftOrderMessageListener implements MessageListenerConcurrently {
    private static final Logger log = LoggerFactory.getLogger(GiftOrderMessageListener.class);

    private final GiftService giftService;
    private final RocketMqProperties properties;
    private final MqDeadLetterService deadLetterService;
    private final MqMetrics metrics;

    public GiftOrderMessageListener(
            GiftService giftService,
            RocketMqProperties properties,
            MqDeadLetterService deadLetterService,
            MqMetrics metrics) {
        this.giftService = giftService;
        this.properties = properties;
        this.deadLetterService = deadLetterService;
        this.metrics = metrics;
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(
            List<MessageExt> messages,
            ConsumeConcurrentlyContext context) {
        for (MessageExt message : messages) {
            try {
                String orderNo = new String(message.getBody(), StandardCharsets.UTF_8);
                if (orderNo.isBlank()) {
                    throw new IllegalArgumentException("礼物订单号不能为空");
                }
                GiftService.GiftOrderProcessingResult result = giftService.processOrder(orderNo);
                if (result.newlySucceeded()) {
                    giftService.publishSuccess(result.order());
                }
                metrics.recordConsume(properties.getGiftConsumerGroup(), "success", message.getReconsumeTimes());
            } catch (IllegalArgumentException exception) {
                deadLetterService.capturePermanentFailure(properties.getGiftConsumerGroup(), message, exception);
                metrics.recordConsume(properties.getGiftConsumerGroup(), "quarantined", message.getReconsumeTimes());
            } catch (RuntimeException exception) {
                metrics.recordConsume(properties.getGiftConsumerGroup(), "failure", message.getReconsumeTimes());
                log.warn("礼物订单消费失败，将等待 RocketMQ 重试", exception);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }
}
