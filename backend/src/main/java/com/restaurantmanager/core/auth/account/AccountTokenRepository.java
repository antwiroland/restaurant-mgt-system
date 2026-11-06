package com.restaurantmanager.core.auth.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccountTokenRepository extends JpaRepository<AccountTokenEntity, UUID> {
    Optional<AccountTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update AccountTokenEntity t set t.usedAt = :now where t.userId = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateOpen(@Param("userId") UUID userId, @Param("purpose") AccountTokenEntity.Purpose purpose, @Param("now") Instant now);
}
