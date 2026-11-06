package com.restaurantmanager.core.payment;

import com.restaurantmanager.core.config.PaymentProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaymentProps.class)
public class PaymentConfig {
    private static final Logger log = LoggerFactory.getLogger(PaymentConfig.class);

    @Bean
    public PaystackClient paystackClient(PaymentProps paymentProps) {
        PaymentProps.Paystack paystack = paymentProps.getPaystack();
        if (paystack.isLive()) {
            String key = paystack.getSecretKey();
            if (key == null || !key.startsWith("sk_")) {
                throw new IllegalStateException("payments.paystack.mode=live requires a real Paystack secret key (sk_test_... or sk_live_...)");
            }
            log.info("Using live Paystack client at {}", paystack.getBaseUrl());
            return new HttpPaystackClient(paystack);
        }
        log.warn("Using STUB Paystack client - no real charges will be made (set PAYSTACK_MODE=live to enable)");
        return new StubPaystackClient();
    }
}
