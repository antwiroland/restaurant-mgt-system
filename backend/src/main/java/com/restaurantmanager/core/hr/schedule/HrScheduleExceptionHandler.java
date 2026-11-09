package com.restaurantmanager.core.hr.schedule;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

/** Returns schedule validation problems with their individual issues (409 when blocking, 422 when warnings need acknowledgement). */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HrScheduleExceptionHandler {

    @ExceptionHandler(ScheduleIssuesException.class)
    public ResponseEntity<Body> handle(ScheduleIssuesException ex) {
        HttpStatus status = ex.blocking() ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status)
                .body(new Body(status.value(), status.name(), ex.getMessage(), Instant.now(), ex.blocking(), ex.getIssues()));
    }

    public record Body(int status, String error, String message, Instant timestamp, boolean blocking, List<ScheduleRules.Issue> issues) {}
}
