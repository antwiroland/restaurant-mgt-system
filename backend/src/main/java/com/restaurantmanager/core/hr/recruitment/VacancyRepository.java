package com.restaurantmanager.core.hr.recruitment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VacancyRepository extends JpaRepository<VacancyEntity, UUID> {
    List<VacancyEntity> findAllByOrderByCreatedAtDesc();

    List<VacancyEntity> findByBranchIdInOrderByCreatedAtDesc(Collection<UUID> branchIds);
}
