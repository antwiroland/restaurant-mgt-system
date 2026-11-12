package com.restaurantmanager.core.hr.recruitment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InterviewRepository extends JpaRepository<InterviewEntity, UUID> {
    List<InterviewEntity> findByApplicationIdOrderByScheduledAtAsc(UUID applicationId);

    List<InterviewEntity> findByApplicationIdIn(Collection<UUID> applicationIds);

    List<InterviewEntity> findByInterviewerIdAndScheduledAtBetweenOrderByScheduledAtAsc(UUID interviewerId, Instant from, Instant to);
}
