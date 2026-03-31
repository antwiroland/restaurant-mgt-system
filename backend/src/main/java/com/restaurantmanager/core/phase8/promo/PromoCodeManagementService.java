package com.restaurantmanager.core.phase8.promo;

import com.restaurantmanager.core.common.ApiException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PromoCodeManagementService {
    private final PromoCodeRepository promoCodeRepository;

    public PromoCodeManagementService(PromoCodeRepository promoCodeRepository) {
        this.promoCodeRepository = promoCodeRepository;
    }

    @Transactional(readOnly = true)
    public List<PromoCodeResponse> listPromoCodes() {
        return promoCodeRepository.findAll().stream()
                .sorted(Comparator
                        .comparing(PromoCodeEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PromoCodeEntity::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PromoCodeResponse createPromoCode(PromoCodeUpsertRequest request) {
        PromoCodeEntity entity = new PromoCodeEntity();
        entity.setUsageCount(0);
        applyRequest(entity, request);
        return toResponse(promoCodeRepository.save(entity));
    }

    @Transactional
    public PromoCodeResponse updatePromoCode(UUID id, PromoCodeUpsertRequest request) {
        PromoCodeEntity entity = promoCodeRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Promo code not found"));
        applyRequest(entity, request);
        return toResponse(promoCodeRepository.save(entity));
    }

    @Transactional
    public PromoCodeResponse updatePromoCodeStatus(UUID id, boolean active) {
        PromoCodeEntity entity = promoCodeRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Promo code not found"));
        entity.setActive(active);
        return toResponse(promoCodeRepository.save(entity));
    }

    private void applyRequest(PromoCodeEntity entity, PromoCodeUpsertRequest request) {
        String normalizedCode = request.code().trim().toUpperCase(Locale.ROOT);
        promoCodeRepository.findByCodeIgnoreCase(normalizedCode)
                .filter(existing -> !existing.getId().equals(entity.getId()))
                .ifPresent(existing -> {
                    throw new ApiException(409, "Promo code already exists");
                });

        if (request.maxDiscount() != null && request.maxDiscount().compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(400, "Max discount cannot be negative");
        }
        if (request.usageLimit() != null && request.usageLimit() < 1) {
            throw new ApiException(400, "Usage limit must be at least 1");
        }

        entity.setCode(normalizedCode);
        entity.setDescription(blankToNull(request.description()));
        entity.setDiscountType(request.discountType());
        entity.setDiscountValue(request.discountValue());
        entity.setMinOrderAmount(request.minOrderAmount());
        entity.setMaxDiscount(request.maxDiscount());
        entity.setExpiryDate(request.expiryDate());
        entity.setUsageLimit(request.usageLimit());
        entity.setActive(request.active());
    }

    private PromoCodeResponse toResponse(PromoCodeEntity entity) {
        return new PromoCodeResponse(
                entity.getId(),
                entity.getCode(),
                entity.getDescription(),
                entity.getDiscountType(),
                entity.getDiscountValue(),
                entity.getMinOrderAmount(),
                entity.getMaxDiscount(),
                entity.getExpiryDate(),
                entity.getUsageLimit(),
                entity.getUsageCount(),
                entity.isActive(),
                entity.getCreatedAt()
        );
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record PromoCodeUpsertRequest(
            @NotBlank @Size(max = 30) String code,
            @Size(max = 1000) String description,
            @NotNull com.restaurantmanager.core.phase8.common.DiscountType discountType,
            @NotNull BigDecimal discountValue,
            @NotNull BigDecimal minOrderAmount,
            BigDecimal maxDiscount,
            Instant expiryDate,
            @Min(1) Integer usageLimit,
            boolean active
    ) {}

    public record PromoCodeStatusRequest(boolean active) {}

    public record PromoCodeResponse(
            UUID id,
            String code,
            String description,
            com.restaurantmanager.core.phase8.common.DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscount,
            Instant expiryDate,
            Integer usageLimit,
            int usageCount,
            boolean active,
            Instant createdAt
    ) {}
}
