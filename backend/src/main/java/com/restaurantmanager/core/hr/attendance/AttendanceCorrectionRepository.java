package com.restaurantmanager.core.hr.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrectionEntity, UUID> {
    List<AttendanceCorrectionEntity> findByStatusOrderByCreatedAtAsc(CorrectionStatus status);

    List<AttendanceCorrectionEntity> findTop200ByOrderByCreatedAtDesc();

    List<AttendanceCorrectionEntity> findByTimeEntryIdOrderByCreatedAtAsc(UUID timeEntryId);

    boolean existsByTimeEntryIdAndStatus(UUID timeEntryId, CorrectionStatus status);
}
