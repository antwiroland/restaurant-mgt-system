package com.restaurantmanager.core.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class EmailConfig {
    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    /**
     * email.provider=smtp sends through Spring's JavaMailSender (configure spring.mail.* / SMTP_* variables);
     * otherwise emails are only logged.
     */
    @Bean
    EmailGateway emailGateway(@Value("${email.provider:log}") String provider,
                              @Value("${email.from:no-reply@restaurant.local}") String from,
                              ObjectProvider<JavaMailSender> mailSender) {
        if ("smtp".equalsIgnoreCase(provider)) {
            JavaMailSender sender = mailSender.getIfAvailable();
            if (sender == null) {
                throw new IllegalStateException("email.provider=smtp requires SPRING_MAIL_HOST (spring.mail.host)");
            }
            log.info("Sending email via SMTP as {}", from);
            return (to, subject, body) -> {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                sender.send(message);
            };
        }
        log.warn("Email provider is 'log' - emails (including password reset links) are logged, not delivered");
        return (to, subject, body) -> log.info("Email (not delivered) to {} - {}\n{}", to, subject, body);
    }
}
