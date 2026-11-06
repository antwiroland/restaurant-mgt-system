package com.restaurantmanager.core.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaxRateRepository extends JpaRepository<TaxRateEntity, UUID> {
    List<TaxRateEntity> findByActiveTrueOrderByDisplayOrderAsc();

    List<TaxRateEntity> findAllByOrderByDisplayOrderAsc();

    boolean existsByCodeIgnoreCase(String code);
}
