package com.restaurantmanager.core.config;

import com.restaurantmanager.core.notification.NotificationProps;
import com.restaurantmanager.core.notification.whatsapp.MetaWhatsAppGateway;
import com.restaurantmanager.core.notification.whatsapp.WhatsAppGateway;
import com.restaurantmanager.core.notification.whatsapp.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(NotificationProps.class)
public class WhatsAppConfig {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppConfig.class);

    @Bean(destroyMethod = "shutdown")
    public ExecutorService notificationExecutor() {
        return new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500),
                runnable -> {
                    Thread thread = new Thread(runnable, "notification-sender");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    @Bean
    public WhatsAppGateway whatsAppGateway(NotificationProps props, ExecutorService notificationExecutor) {
        NotificationProps.WhatsApp whatsapp = props.getWhatsapp();
        if ("meta".equalsIgnoreCase(whatsapp.getProvider())) {
            if (isBlank(whatsapp.getAccessToken()) || isBlank(whatsapp.getPhoneNumberId())) {
                throw new IllegalStateException("notifications.whatsapp.provider=meta requires access-token and phone-number-id");
            }
            log.info("Using WhatsApp Cloud API gateway (phone number id {})", whatsapp.getPhoneNumberId());
            WhatsAppGateway delegate = new MetaWhatsAppGateway(props);
            // Deliver off the request thread so slow provider calls never block order/payment transactions.
            return (phone, message) -> notificationExecutor.execute(() -> {
                try {
                    delegate.send(phone, message);
                } catch (RuntimeException ex) {
                    log.warn("WhatsApp delivery to {} failed: {}", phone, ex.getMessage());
                }
            });
        }
        log.info("WhatsApp provider is 'log' - messages are logged, not delivered");
        return (phone, message) -> log.info("WhatsApp notification (not delivered) for {}: {}", phone, message);
    }

    @Bean
    public WhatsAppService whatsAppService(WhatsAppGateway whatsAppGateway) {
        return new WhatsAppService(whatsAppGateway, error -> log.warn(error));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
