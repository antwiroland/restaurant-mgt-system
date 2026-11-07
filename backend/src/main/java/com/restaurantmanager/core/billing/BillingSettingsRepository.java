package com.restaurantmanager.core.billing;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingSettingsRepository extends JpaRepository<BillingSettingsEntity, Integer> {
}
