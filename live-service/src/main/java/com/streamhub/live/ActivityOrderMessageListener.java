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
public class ActivityOrderMessageListener implements MessageListenerConcurrently {
    private static final Logger log = LoggerFactory.getLogger(ActivityOrderMessageListener.class);

    private final ActivityService activityService;

    public ActivityOrderMessageListener(ActivityService activityService) {
        this.activityService = activityService;
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(
            List<MessageExt> messages,
            ConsumeConcurrentlyContext context) {
        try {
            for (MessageExt message : messages) {
                String body = new String(message.getBody(), StandardCharsets.UTF_8);
                int separator = body.indexOf('|');
                if (separator <= 0 || separator == body.length() - 1) {
                    throw new IllegalArgumentException("非法活动消息");
                }
                String type = body.substring(0, separator);
                String orderNo = body.substring(separator + 1);
                log.info("处理活动订单消息 type={} orderNo={}", type, orderNo);
                if ("CREATE".equals(type)) {
                    activityService.processCreate(orderNo);
                } else if ("CLOSE".equals(type)) {
                    activityService.processClose(orderNo);
                } else {
                    throw new IllegalArgumentException("未知活动消息类型: " + type);
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (RuntimeException exception) {
            log.warn("活动订单消息消费失败，将等待 RocketMQ 重试", exception);
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }
}
