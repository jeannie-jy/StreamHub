package com.streamhub.live;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.streamhub")
@EnableScheduling
@EnableConfigurationProperties(MediaProperties.class)
public class LiveServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LiveServiceApplication.class, args);
    }
}
