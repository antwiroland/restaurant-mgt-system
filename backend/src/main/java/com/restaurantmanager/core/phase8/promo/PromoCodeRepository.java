package com.restaurantmanager.core.phase8.promo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCodeEntity, UUID> {
    Optional<PromoCodeEntity> findByCodeIgnoreCase(String code);
}
