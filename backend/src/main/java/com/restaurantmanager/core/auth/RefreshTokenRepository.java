package com.restaurantmanager.core.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update RefreshTokenEntity t set t.revoked = true where t.user.id = :userId and t.revoked = false")
    int revokeAllForUser(@org.springframework.data.repository.query.Param("userId") UUID userId);
}
