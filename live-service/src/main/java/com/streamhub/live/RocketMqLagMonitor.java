package com.streamhub.live;

import java.util.Set;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.store.ReadOffsetType;
import org.apache.rocketmq.common.message.MessageQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RocketMqLagMonitor {
    private static final Logger log = LoggerFactory.getLogger(RocketMqLagMonitor.class);

    private final DefaultMQPushConsumer giftConsumer;
    private final DefaultMQPushConsumer activityConsumer;
    private final RocketMqProperties properties;
    private final MqMetrics metrics;

    public RocketMqLagMonitor(
            @Qualifier("giftOrderConsumer") DefaultMQPushConsumer giftConsumer,
            @Qualifier("activityOrderConsumer") DefaultMQPushConsumer activityConsumer,
            RocketMqProperties properties,
            MqMetrics metrics) {
        this.giftConsumer = giftConsumer;
        this.activityConsumer = activityConsumer;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Scheduled(
            initialDelayString = "${streamhub.rocketmq.lag-monitor-initial-delay-ms:15000}",
            fixedDelayString = "${streamhub.rocketmq.lag-monitor-fixed-delay-ms:15000}")
    public void sample() {
        sampleConsumer(giftConsumer, properties.getGiftTopic(), properties.getGiftConsumerGroup());
        sampleConsumer(activityConsumer, properties.getActivityTopic(), properties.getActivityConsumerGroup());
    }

    private void sampleConsumer(DefaultMQPushConsumer consumer, String topic, String consumerGroup) {
        try {
            Set<MessageQueue> queues = consumer.fetchSubscribeMessageQueues(topic);
            long lag = 0;
            for (MessageQueue queue : queues) {
                long maxOffset = consumer.maxOffset(queue);
                long consumeOffset = consumer.getOffsetStore().readOffset(
                        queue,
                        ReadOffsetType.MEMORY_FIRST_THEN_STORE);
                if (consumeOffset < 0) {
                    consumeOffset = consumer.minOffset(queue);
                }
                lag += Math.max(0, maxOffset - consumeOffset);
            }
            metrics.updateConsumerLag(consumerGroup, lag);
        } catch (Exception exception) {
            log.debug("读取 RocketMQ 消费积压失败 consumerGroup={}", consumerGroup, exception);
        }
    }
}
