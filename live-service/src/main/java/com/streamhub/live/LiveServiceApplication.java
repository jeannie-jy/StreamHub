package com.streamhub.live;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.streamhub")
@EnableScheduling
@EnableDiscoveryClient
@EnableFeignClients
@EnableConfigurationProperties(MediaProperties.class)
public class LiveServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LiveServiceApplication.class, args);
    }
}
