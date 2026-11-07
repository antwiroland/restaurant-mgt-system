package com.restaurantmanager.core.profile;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class RestaurantProfileService {
    private final RestaurantProfileRepository repository;
    private final String defaultName;

    public RestaurantProfileService(RestaurantProfileRepository repository,
                                    @Value("${app.venue-name:Restaurant}") String defaultName) {
        this.repository = repository;
        this.defaultName = defaultName;
    }

    @Transactional(readOnly = true)
    public ProfileView get() {
        return toView(load());
    }

    /** Used by receipts and notifications. */
    @Transactional(readOnly = true)
    public String displayName() {
        return load().getName();
    }

    @Transactional
    public ProfileView update(ProfileRequest request, UUID actorId) {
        RestaurantProfileEntity profile = load();
        profile.setName(request.name().trim());
        profile.setTagline(blankToNull(request.tagline()));
        profile.setAbout(blankToNull(request.about()));
        profile.setAddress(blankToNull(request.address()));
        profile.setPhone(blankToNull(request.phone()));
        profile.setEmail(blankToNull(request.email()));
        profile.setWebsiteUrl(blankToNull(request.websiteUrl()));
        profile.setHeroImageUrl(blankToNull(request.heroImageUrl()));
        profile.setCurrency(request.currency() == null || request.currency().isBlank() ? "GHS" : request.currency().trim().toUpperCase());
        List<String> hours = request.openingHours() == null ? List.of()
                : request.openingHours().stream().map(String::trim).filter(line -> !line.isEmpty()).toList();
        profile.setOpeningHours(hours.isEmpty() ? null : String.join("\n", hours));
        profile.setLatitude(request.latitude());
        profile.setLongitude(request.longitude());
        profile.setReceiptFooter(blankToNull(request.receiptFooter()));
        profile.setUpdatedBy(actorId);
        return toView(repository.save(profile));
    }

    private RestaurantProfileEntity load() {
        return repository.findById(RestaurantProfileEntity.SINGLETON_ID).orElseGet(() -> {
            RestaurantProfileEntity fresh = new RestaurantProfileEntity();
            fresh.setName(defaultName);
            return fresh;
        });
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ProfileView toView(RestaurantProfileEntity e) {
        List<String> hours = e.getOpeningHours() == null ? List.of()
                : Arrays.stream(e.getOpeningHours().split("\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new ProfileView(e.getName(), e.getTagline(), e.getAbout(), e.getAddress(), e.getPhone(), e.getEmail(),
                e.getWebsiteUrl(), e.getHeroImageUrl(), e.getCurrency(), hours, e.getLatitude(), e.getLongitude(),
                e.getReceiptFooter(), e.getUpdatedAt());
    }

    public record ProfileView(String name, String tagline, String about, String address, String phone, String email,
                              String websiteUrl, String heroImageUrl, String currency, List<String> openingHours,
                              BigDecimal latitude, BigDecimal longitude, String receiptFooter, Instant updatedAt) {}

    public record ProfileRequest(@NotBlank @Size(max = 120) String name,
                                 @Size(max = 160) String tagline,
                                 @Size(max = 4000) String about,
                                 @Size(max = 255) String address,
                                 @Size(max = 40) String phone,
                                 @Email @Size(max = 160) String email,
                                 @Size(max = 255) @Pattern(regexp = "^$|^https?://.*", message = "must start with http:// or https://") String websiteUrl,
                                 @Size(max = 1000) @Pattern(regexp = "^$|^https?://.*", message = "must start with http:// or https://") String heroImageUrl,
                                 @Size(max = 8) String currency,
                                 @Size(max = 14) List<@Size(max = 80) String> openingHours,
                                 @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
                                 @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
                                 @Size(max = 500) String receiptFooter) {}
}
