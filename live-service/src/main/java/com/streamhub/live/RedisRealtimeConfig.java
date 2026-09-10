package com.streamhub.live;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisRealtimeConfig {
    @Bean
    public ChannelTopic roomEventTopic(
            @Value("${streamhub.realtime.channel:live:room:events}") String channel) {
        return new ChannelTopic(channel);
    }

    @Bean
    public RedisMessageListenerContainer roomEventListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisRoomEventListener listener,
            ChannelTopic roomEventTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, roomEventTopic);
        return container;
    }
}
