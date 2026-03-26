package com.restaurantmanager.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(exclude = {RedisRepositoriesAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
public class BackendCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendCoreApplication.class, args);
    }
}
