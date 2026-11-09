package com.restaurantmanager.core.hr.timesheet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TimesheetLineRepository extends JpaRepository<TimesheetLineEntity, UUID> {
    List<TimesheetLineEntity> findByTimesheetIdOrderByWorkDateAscSortOrderAsc(UUID timesheetId);

    @Modifying
    @Query("delete from TimesheetLineEntity l where l.timesheetId = :timesheetId")
    void deleteByTimesheetId(@Param("timesheetId") UUID timesheetId);
}
