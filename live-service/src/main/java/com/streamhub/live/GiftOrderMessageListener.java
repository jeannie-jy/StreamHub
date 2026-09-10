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

    public GiftOrderMessageListener(GiftService giftService) {
        this.giftService = giftService;
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(
            List<MessageExt> messages,
            ConsumeConcurrentlyContext context) {
        try {
            for (MessageExt message : messages) {
                String orderNo = new String(message.getBody(), StandardCharsets.UTF_8);
                GiftService.GiftOrderProcessingResult result = giftService.processOrder(orderNo);
                if (result.newlySucceeded()) {
                    giftService.publishSuccess(result.order());
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (RuntimeException exception) {
            log.warn("礼物订单消费失败，将等待 RocketMQ 重试", exception);
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }
}
