package com.restaurantmanager.core.hr.attendance;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Persists rejected clock events in their own transaction, so the record survives the rollback of the failed action. */
@Component
public class AttendanceEventRecorder {
    private final AttendanceEventRepository repository;

    public AttendanceEventRecorder(AttendanceEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejected(String key, UUID employeeId, ClockAction action, Instant occurredAt, AttendanceSource source, String terminalId,
                         UUID branchId, UUID actorUserId, String message) {
        if (repository.findByIdempotencyKey(key).isPresent()) {
            return;
        }
        AttendanceEventEntity event = new AttendanceEventEntity();
        event.setIdempotencyKey(key);
        event.setEmployeeId(employeeId);
        event.setEventType(action);
        event.setOccurredAt(occurredAt);
        event.setSource(source.name());
        event.setTerminalId(terminalId);
        event.setBranchId(branchId);
        event.setOutcome("REJECTED");
        event.setMessage(message == null ? null : message.length() > 300 ? message.substring(0, 300) : message);
        event.setRecordedBy(actorUserId);
        repository.save(event);
    }
}
