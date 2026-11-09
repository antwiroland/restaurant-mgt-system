package com.restaurantmanager.core.hr.org;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PositionRepository extends JpaRepository<PositionEntity, UUID> {
    @EntityGraph(attributePaths = "department")
    List<PositionEntity> findAllByOrderByTitleAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    boolean existsByDepartmentIdAndActiveTrue(UUID departmentId);
}
