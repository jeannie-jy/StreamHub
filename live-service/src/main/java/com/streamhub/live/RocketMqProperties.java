package com.streamhub.live;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "streamhub.rocketmq")
public class RocketMqProperties {
    private String namesrvAddr = "localhost:9876";
    private String giftTopic = "STREAMHUB_GIFT_ORDER";
    private String giftProducerGroup = "streamhub-gift-producer";
    private String giftConsumerGroup = "streamhub-gift-consumer";
    private String activityTopic = "STREAMHUB_ACTIVITY_ORDER";
    private String activityProducerGroup = "streamhub-activity-producer";
    private String activityConsumerGroup = "streamhub-activity-consumer-v2";
    private int timeoutDelayLevel = 3;
    private int consumeThreadMin = 4;
    private int consumeThreadMax = 8;
    private int consumeMessageBatchMaxSize = 1;
    private int maxReconsumeTimes = 5;
    private int producerRetryTimes = 2;
    private boolean deadLetterEnabled = true;
    private String deadLetterConsumerGroup = "streamhub-dead-letter-auditor";
    private boolean outboxEnabled = true;
    private int outboxBatchSize = 100;
    private int outboxMaxAttempts = 20;
    private long outboxBaseRetryDelayMs = 1000;

    public String getNamesrvAddr() {
        return namesrvAddr;
    }

    public void setNamesrvAddr(String namesrvAddr) {
        this.namesrvAddr = namesrvAddr;
    }

    public String getGiftTopic() {
        return giftTopic;
    }

    public void setGiftTopic(String giftTopic) {
        this.giftTopic = giftTopic;
    }

    public String getGiftProducerGroup() {
        return giftProducerGroup;
    }

    public void setGiftProducerGroup(String giftProducerGroup) {
        this.giftProducerGroup = giftProducerGroup;
    }

    public String getGiftConsumerGroup() {
        return giftConsumerGroup;
    }

    public void setGiftConsumerGroup(String giftConsumerGroup) {
        this.giftConsumerGroup = giftConsumerGroup;
    }

    public String getActivityTopic() {
        return activityTopic;
    }

    public void setActivityTopic(String activityTopic) {
        this.activityTopic = activityTopic;
    }

    public String getActivityProducerGroup() {
        return activityProducerGroup;
    }

    public void setActivityProducerGroup(String activityProducerGroup) {
        this.activityProducerGroup = activityProducerGroup;
    }

    public String getActivityConsumerGroup() {
        return activityConsumerGroup;
    }

    public void setActivityConsumerGroup(String activityConsumerGroup) {
        this.activityConsumerGroup = activityConsumerGroup;
    }

    public int getTimeoutDelayLevel() {
        return timeoutDelayLevel;
    }

    public void setTimeoutDelayLevel(int timeoutDelayLevel) {
        this.timeoutDelayLevel = timeoutDelayLevel;
    }

    public int getConsumeThreadMin() {
        return consumeThreadMin;
    }

    public void setConsumeThreadMin(int consumeThreadMin) {
        this.consumeThreadMin = consumeThreadMin;
    }

    public int getConsumeThreadMax() {
        return consumeThreadMax;
    }

    public void setConsumeThreadMax(int consumeThreadMax) {
        this.consumeThreadMax = consumeThreadMax;
    }

    public int getConsumeMessageBatchMaxSize() {
        return consumeMessageBatchMaxSize;
    }

    public void setConsumeMessageBatchMaxSize(int consumeMessageBatchMaxSize) {
        this.consumeMessageBatchMaxSize = consumeMessageBatchMaxSize;
    }

    public int getMaxReconsumeTimes() {
        return maxReconsumeTimes;
    }

    public void setMaxReconsumeTimes(int maxReconsumeTimes) {
        this.maxReconsumeTimes = maxReconsumeTimes;
    }

    public int getProducerRetryTimes() {
        return producerRetryTimes;
    }

    public void setProducerRetryTimes(int producerRetryTimes) {
        this.producerRetryTimes = producerRetryTimes;
    }

    public boolean isDeadLetterEnabled() {
        return deadLetterEnabled;
    }

    public void setDeadLetterEnabled(boolean deadLetterEnabled) {
        this.deadLetterEnabled = deadLetterEnabled;
    }

    public String getDeadLetterConsumerGroup() {
        return deadLetterConsumerGroup;
    }

    public void setDeadLetterConsumerGroup(String deadLetterConsumerGroup) {
        this.deadLetterConsumerGroup = deadLetterConsumerGroup;
    }

    public boolean isOutboxEnabled() {
        return outboxEnabled;
    }

    public void setOutboxEnabled(boolean outboxEnabled) {
        this.outboxEnabled = outboxEnabled;
    }

    public int getOutboxBatchSize() {
        return outboxBatchSize;
    }

    public void setOutboxBatchSize(int outboxBatchSize) {
        this.outboxBatchSize = outboxBatchSize;
    }

    public int getOutboxMaxAttempts() {
        return outboxMaxAttempts;
    }

    public void setOutboxMaxAttempts(int outboxMaxAttempts) {
        this.outboxMaxAttempts = outboxMaxAttempts;
    }

    public long getOutboxBaseRetryDelayMs() {
        return outboxBaseRetryDelayMs;
    }

    public void setOutboxBaseRetryDelayMs(long outboxBaseRetryDelayMs) {
        this.outboxBaseRetryDelayMs = outboxBaseRetryDelayMs;
    }
}
