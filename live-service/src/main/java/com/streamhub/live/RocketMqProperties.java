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
}
