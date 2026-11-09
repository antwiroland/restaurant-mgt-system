package com.restaurantmanager.core.hr.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceBreakRepository extends JpaRepository<AttendanceBreakEntity, UUID> {
    List<AttendanceBreakEntity> findByTimeEntryIdOrderByStartedAtAsc(UUID timeEntryId);

    List<AttendanceBreakEntity> findByTimeEntryIdIn(Collection<UUID> timeEntryIds);

    Optional<AttendanceBreakEntity> findFirstByTimeEntryIdAndEndedAtIsNull(UUID timeEntryId);
}
