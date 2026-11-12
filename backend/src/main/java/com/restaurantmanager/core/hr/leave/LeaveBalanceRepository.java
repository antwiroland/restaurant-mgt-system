package com.restaurantmanager.core.hr.leave;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalanceEntity, UUID> {
    Optional<LeaveBalanceEntity> findByEmployeeIdAndLeaveTypeIdAndLeaveYear(UUID employeeId, UUID leaveTypeId, int leaveYear);

    /** Row lock so concurrent requests cannot both spend the last days of a balance. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from LeaveBalanceEntity b where b.id = :id")
    Optional<LeaveBalanceEntity> lockById(@Param("id") UUID id);

    List<LeaveBalanceEntity> findByEmployeeIdAndLeaveYear(UUID employeeId, int leaveYear);
}
