package com.restaurantmanager.core.favourite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FavouriteRepository extends JpaRepository<FavouriteEntity, FavouriteEntity.Key> {
    List<FavouriteEntity> findById_UserIdOrderByCreatedAtDesc(UUID userId);
}
