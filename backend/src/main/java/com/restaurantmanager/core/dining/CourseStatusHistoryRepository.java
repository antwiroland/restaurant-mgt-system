package com.restaurantmanager.core.dining;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseStatusHistoryRepository extends JpaRepository<CourseStatusHistoryEntity, UUID> {

    List<CourseStatusHistoryEntity> findByDiningOrderCourseIdOrderByCreatedAtAsc(UUID diningOrderCourseId);
}
