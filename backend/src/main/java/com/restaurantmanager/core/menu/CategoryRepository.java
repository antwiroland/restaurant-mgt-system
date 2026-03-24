package com.restaurantmanager.core.menu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {
    List<CategoryEntity> findAllByOrderByDisplayOrderAscNameAsc();

    List<CategoryEntity> findByActiveTrueOrderByDisplayOrderAscNameAsc();

    Optional<CategoryEntity> findByIdAndActiveTrue(UUID id);

    boolean existsByNameIgnoreCase(String name);
}
