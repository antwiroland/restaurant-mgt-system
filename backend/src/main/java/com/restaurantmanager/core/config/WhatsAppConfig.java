package com.restaurantmanager.core.config;

import com.restaurantmanager.core.phase8.whatsapp.WhatsAppGateway;
import com.restaurantmanager.core.phase8.whatsapp.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WhatsAppConfig {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppConfig.class);

    @Bean
    public WhatsAppGateway whatsAppGateway() {
        return (phone, message) -> log.info("WhatsApp notification queued for {}: {}", phone, message);
    }

    @Bean
    public WhatsAppService whatsAppService(WhatsAppGateway whatsAppGateway) {
        return new WhatsAppService(whatsAppGateway, error -> log.warn(error));
    }
}
