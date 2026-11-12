package com.restaurantmanager.core.hr.tips;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TipRuleRepository extends JpaRepository<TipRuleEntity, UUID> {
    List<TipRuleEntity> findBySourceOrderByEffectiveFromDesc(TipSource source);

    List<TipRuleEntity> findAllByOrderBySourceAscEffectiveFromDesc();
}
