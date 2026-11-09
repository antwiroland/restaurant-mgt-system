package com.restaurantmanager.core.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    Optional<UserEntity> findByPhone(String phone);

    Optional<UserEntity> findByEmailIgnoreCase(String email);

    java.util.List<UserEntity> findByRoleAndActiveTrueOrderByNameAsc(com.restaurantmanager.core.common.Role role);

    java.util.List<UserEntity> findByRoleInAndActiveTrueOrderByNameAsc(java.util.Collection<com.restaurantmanager.core.common.Role> roles);
}
