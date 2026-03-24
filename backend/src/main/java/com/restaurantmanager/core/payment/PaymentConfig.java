package com.restaurantmanager.core.payment;

import com.restaurantmanager.core.config.PaymentProps;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaymentProps.class)
public class PaymentConfig {

    @Bean
    public PaystackClient paystackClient() {
        return new StubPaystackClient();
    }
}
