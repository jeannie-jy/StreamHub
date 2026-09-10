package com.streamhub.live;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RocketMqProperties.class)
public class RocketMqConfig {
    @Bean(name = "giftOrderProducer", initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQProducer giftOrderProducer(RocketMqProperties properties) {
        DefaultMQProducer producer = new DefaultMQProducer(properties.getGiftProducerGroup());
        producer.setNamesrvAddr(properties.getNamesrvAddr());
        return producer;
    }

    @Bean(name = "giftOrderConsumer", initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQPushConsumer giftOrderConsumer(
            RocketMqProperties properties,
            GiftOrderMessageListener messageListener) throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(properties.getGiftConsumerGroup());
        consumer.setNamesrvAddr(properties.getNamesrvAddr());
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.subscribe(properties.getGiftTopic(), "*");
        consumer.registerMessageListener(messageListener);
        return consumer;
    }

    @Bean(name = "activityOrderProducer", initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQProducer activityOrderProducer(RocketMqProperties properties) {
        DefaultMQProducer producer = new DefaultMQProducer(properties.getActivityProducerGroup());
        producer.setNamesrvAddr(properties.getNamesrvAddr());
        return producer;
    }

    @Bean(name = "activityOrderConsumer", initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQPushConsumer activityOrderConsumer(
            RocketMqProperties properties,
            ActivityOrderMessageListener messageListener) throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(properties.getActivityConsumerGroup());
        consumer.setNamesrvAddr(properties.getNamesrvAddr());
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.subscribe(properties.getActivityTopic(), "*");
        consumer.registerMessageListener(messageListener);
        return consumer;
    }
}
