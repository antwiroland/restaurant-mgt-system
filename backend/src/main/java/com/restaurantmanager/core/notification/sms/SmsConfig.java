package com.restaurantmanager.core.notification.sms;

import com.restaurantmanager.core.notification.NotificationProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SmsConfig {
    private static final Logger log = LoggerFactory.getLogger(SmsConfig.class);

    @Bean
    SmsGateway smsGateway(NotificationProps props) {
        String provider = props.getSms().getProvider() == null ? "log" : props.getSms().getProvider().trim().toLowerCase();
        return switch (provider) {
            case "hubtel" -> {
                log.info("Using Hubtel SMS gateway");
                yield HttpSmsGateways.hubtel(props);
            }
            case "arkesel" -> {
                log.info("Using Arkesel SMS gateway");
                yield HttpSmsGateways.arkesel(props);
            }
            default -> {
                log.warn("SMS provider is 'log' - SMS (including OTP codes) are logged, not delivered");
                yield (phone, message) -> log.info("SMS (not delivered) to {}: {}", phone, message);
            }
        };
    }
}
