package com.streamhub.live;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.streamhub")
public class LiveServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LiveServiceApplication.class, args);
    }
}
