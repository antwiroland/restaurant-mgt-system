package com.restaurantmanager.core.auth.otp;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.config.SecurityProps;
import com.restaurantmanager.core.notification.sms.SmsGateway;
import com.restaurantmanager.core.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Issues and verifies SMS one-time passcodes. Codes are stored only as salted SHA-256 hashes, expire quickly,
 * are rate limited per phone and lock after too many wrong attempts.
 */
@Service
@EnableConfigurationProperties(OtpProps.class)
public class OtpService {
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpChallengeRepository repository;
    private final UserRepository userRepository;
    private final SmsGateway smsGateway;
    private final OtpProps props;
    private final String pepper;

    public OtpService(OtpChallengeRepository repository,
                      UserRepository userRepository,
                      SmsGateway smsGateway,
                      OtpProps props,
                      SecurityProps securityProps) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.smsGateway = smsGateway;
        this.props = props;
        this.pepper = securityProps.getJwt().getSecret();
    }

    public OtpProps props() {
        return props;
    }

    @Transactional
    public OtpRequestResult request(String rawPhone, OtpPurpose purpose) {
        String phone = normalize(rawPhone);
        Instant now = Instant.now();

        repository.findFirstByPhoneOrderByCreatedAtDesc(phone).ifPresent(last -> {
            long elapsed = Duration.between(last.getCreatedAt(), now).toSeconds();
            if (elapsed < props.getResendCooldownSeconds()) {
                throw new ApiException(429, "Please wait " + (props.getResendCooldownSeconds() - elapsed) + "s before requesting another code");
            }
        });
        if (repository.countByPhoneAndCreatedAtAfter(phone, now.minus(Duration.ofHours(1))) >= props.getMaxRequestsPerHour()) {
            throw new ApiException(429, "Too many codes requested. Try again later");
        }

        boolean userExists = userRepository.existsByPhone(phone);
        if (purpose == OtpPurpose.REGISTER && userExists) {
            throw new ApiException(409, "Phone already registered. Log in instead");
        }

        OtpChallengeEntity challenge = new OtpChallengeEntity();
        challenge.setId(java.util.UUID.randomUUID());
        challenge.setPhone(phone);
        challenge.setPurpose(purpose);
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plusSeconds(props.getTtlSeconds()));
        String code = generateCode();
        challenge.setCodeHash(hash(challenge, code));
        repository.save(challenge);

        // For LOGIN we never reveal whether the phone is registered; only real customers get an SMS.
        if (purpose == OtpPurpose.REGISTER || userExists) {
            try {
                smsGateway.send(phone, "%s: your verification code is %s. It expires in %d minutes. Do not share it."
                        .formatted(props.getAppName(), code, Math.max(1, props.getTtlSeconds() / 60)));
            } catch (RuntimeException ex) {
                log.warn("OTP SMS delivery failed: {}", ex.getMessage());
                throw new ApiException(502, "Could not send verification SMS. Try again shortly");
            }
        } else {
            log.info("OTP login requested for unregistered phone; no SMS sent");
        }
        return new OtpRequestResult(props.getTtlSeconds(), props.getResendCooldownSeconds());
    }

    /**
     * Verifies and consumes the latest open code for the phone and purpose.
     *
     * @return the normalised phone number
     */
    @Transactional(noRollbackFor = ApiException.class)
    public String verify(String rawPhone, String code, OtpPurpose purpose) {
        String phone = normalize(rawPhone);
        OtpChallengeEntity challenge = repository
                .findFirstByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(phone, purpose)
                .orElseThrow(() -> new ApiException(400, "Invalid or expired code"));
        if (challenge.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(400, "Invalid or expired code");
        }
        if (challenge.getAttempts() >= props.getMaxAttempts()) {
            throw new ApiException(429, "Too many incorrect attempts. Request a new code");
        }
        String candidate = code == null ? "" : code.trim();
        if (!MessageDigest.isEqual(hash(challenge, candidate).getBytes(StandardCharsets.UTF_8),
                challenge.getCodeHash().getBytes(StandardCharsets.UTF_8))) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            repository.save(challenge);
            throw new ApiException(400, "Invalid or expired code");
        }
        challenge.setConsumedAt(Instant.now());
        repository.save(challenge);
        return phone;
    }

    public static String normalize(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new ApiException(400, "Phone number is required");
        }
        String trimmed = phone.trim().replaceAll("[\\s-]", "");
        if (!trimmed.matches("\\+?[0-9]{9,15}")) {
            throw new ApiException(400, "Invalid phone number");
        }
        return trimmed;
    }

    private String generateCode() {
        int length = Math.max(4, Math.min(8, props.getCodeLength()));
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private String hash(OtpChallengeEntity challenge, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(pepper.getBytes(StandardCharsets.UTF_8));
            digest.update(challenge.getId().toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record OtpRequestResult(int expiresInSeconds, int resendAfterSeconds) {}
}
