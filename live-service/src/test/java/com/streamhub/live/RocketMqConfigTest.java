package com.streamhub.live;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.junit.jupiter.api.Test;

class RocketMqConfigTest {
    @Test
    void appliesExplicitConsumerAndProducerReliabilitySettings() throws Exception {
        RocketMqProperties properties = new RocketMqProperties();
        properties.setConsumeThreadMin(3);
        properties.setConsumeThreadMax(7);
        properties.setConsumeMessageBatchMaxSize(1);
        properties.setMaxReconsumeTimes(6);
        properties.setProducerRetryTimes(4);
        RocketMqConfig config = new RocketMqConfig();

        DefaultMQPushConsumer consumer = config.giftOrderConsumer(
                properties,
                new GiftOrderMessageListener(null, properties, null, null));
        DefaultMQProducer producer = config.giftOrderProducer(properties);

        assertThat(consumer.getConsumeThreadMin()).isEqualTo(3);
        assertThat(consumer.getConsumeThreadMax()).isEqualTo(7);
        assertThat(consumer.getConsumeMessageBatchMaxSize()).isEqualTo(1);
        assertThat(consumer.getMaxReconsumeTimes()).isEqualTo(6);
        assertThat(producer.getRetryTimesWhenSendFailed()).isEqualTo(4);
    }
}
