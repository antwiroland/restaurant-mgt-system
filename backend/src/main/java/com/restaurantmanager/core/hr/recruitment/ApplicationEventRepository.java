package com.restaurantmanager.core.hr.recruitment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApplicationEventRepository extends JpaRepository<ApplicationEventEntity, UUID> {
    List<ApplicationEventEntity> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}
