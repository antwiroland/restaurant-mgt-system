package com.restaurantmanager.core.hr.timesheet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PublicHolidayRepository extends JpaRepository<PublicHolidayEntity, UUID> {
    List<PublicHolidayEntity> findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate from, LocalDate to);

    @Query("select h from PublicHolidayEntity h where h.holidayDate = :day and (h.branchId is null or h.branchId = :branchId)")
    List<PublicHolidayEntity> findObserved(@Param("day") LocalDate day, @Param("branchId") UUID branchId);

    boolean existsByHolidayDateAndBranchIdIsNull(LocalDate day);

    boolean existsByHolidayDateAndBranchId(LocalDate day, UUID branchId);
}
