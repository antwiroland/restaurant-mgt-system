package com.restaurantmanager.core.promotion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCodeEntity, UUID> {
    Optional<PromoCodeEntity> findByCodeIgnoreCase(String code);

    /** Atomically consumes one use; returns 0 when the usage limit has already been reached. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PromoCodeEntity p set p.usageCount = p.usageCount + 1
            where p.id = :id and (p.usageLimit is null or p.usageCount < p.usageLimit)
            """)
    int consumeUsage(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PromoCodeEntity p set p.usageCount = p.usageCount - 1 where upper(p.code) = upper(:code) and p.usageCount > 0")
    int releaseUsage(@Param("code") String code);
}
