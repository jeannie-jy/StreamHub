package com.streamhub.live;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.MixAll;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
        producer.setRetryTimesWhenSendFailed(properties.getProducerRetryTimes());
        return producer;
    }

    @Bean(name = "giftOrderConsumer", initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQPushConsumer giftOrderConsumer(
            RocketMqProperties properties,
            GiftOrderMessageListener messageListener) throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(properties.getGiftConsumerGroup());
        consumer.setNamesrvAddr(properties.getNamesrvAddr());
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        configureConsumer(consumer, properties);
        consumer.subscribe(properties.getGiftTopic(), "*");
        consumer.registerMessageListener(messageListener);
        return consumer;
    }

    @Bean(name = "activityOrderProducer", initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQProducer activityOrderProducer(RocketMqProperties properties) {
        DefaultMQProducer producer = new DefaultMQProducer(properties.getActivityProducerGroup());
        producer.setNamesrvAddr(properties.getNamesrvAddr());
        producer.setRetryTimesWhenSendFailed(properties.getProducerRetryTimes());
        return producer;
    }

    @Bean(name = "activityOrderConsumer", initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQPushConsumer activityOrderConsumer(
            RocketMqProperties properties,
            ActivityOrderMessageListener messageListener) throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(properties.getActivityConsumerGroup());
        consumer.setNamesrvAddr(properties.getNamesrvAddr());
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        configureConsumer(consumer, properties);
        consumer.subscribe(properties.getActivityTopic(), "*");
        consumer.registerMessageListener(messageListener);
        return consumer;
    }

    @Bean(name = "deadLetterConsumer", initMethod = "start", destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "streamhub.rocketmq", name = "dead-letter-enabled", havingValue = "true", matchIfMissing = true)
    public DefaultMQPushConsumer deadLetterConsumer(
            RocketMqProperties properties,
            DeadLetterMessageListener messageListener) throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(properties.getDeadLetterConsumerGroup());
        consumer.setNamesrvAddr(properties.getNamesrvAddr());
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.setConsumeThreadMin(1);
        consumer.setConsumeThreadMax(Math.max(1, Math.min(2, properties.getConsumeThreadMax())));
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.setMaxReconsumeTimes(properties.getMaxReconsumeTimes());
        consumer.subscribe(MixAll.getDLQTopic(properties.getGiftConsumerGroup()), "*");
        consumer.subscribe(MixAll.getDLQTopic(properties.getActivityConsumerGroup()), "*");
        consumer.registerMessageListener(messageListener);
        return consumer;
    }

    private void configureConsumer(DefaultMQPushConsumer consumer, RocketMqProperties properties) {
        int minThreads = Math.max(1, properties.getConsumeThreadMin());
        int maxThreads = Math.max(minThreads, properties.getConsumeThreadMax());
        consumer.setConsumeThreadMin(minThreads);
        consumer.setConsumeThreadMax(maxThreads);
        consumer.setConsumeMessageBatchMaxSize(Math.max(1, properties.getConsumeMessageBatchMaxSize()));
        consumer.setMaxReconsumeTimes(Math.max(0, properties.getMaxReconsumeTimes()));
    }
}
