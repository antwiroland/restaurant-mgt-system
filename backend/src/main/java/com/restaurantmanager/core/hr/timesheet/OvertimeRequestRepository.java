package com.restaurantmanager.core.hr.timesheet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequestEntity, UUID> {
    List<OvertimeRequestEntity> findByStatusOrderByCreatedAtAsc(OvertimeRequestStatus status);

    List<OvertimeRequestEntity> findTop200ByOrderByCreatedAtDesc();

    List<OvertimeRequestEntity> findByEmployeeIdAndStatusAndWorkDateBetween(UUID employeeId, OvertimeRequestStatus status, LocalDate from, LocalDate to);
}
