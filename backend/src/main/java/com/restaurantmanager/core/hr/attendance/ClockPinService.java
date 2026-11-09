package com.restaurantmanager.core.hr.attendance;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Employee clock PINs: hashed with the application's BCrypt encoder, never logged or returned. Failed attempts are counted
 * in their own transaction (so a rejected clock request cannot roll the counter back) and lock the PIN after a limit.
 */
@Service
public class ClockPinService {
    private static final Pattern DIGITS = Pattern.compile("\\d{4,6}");

    public enum Result { OK, WRONG, LOCKED, NOT_SET }

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final int maxAttempts;
    private final Duration lockout;

    public ClockPinService(EmployeeRepository employeeRepository, PasswordEncoder passwordEncoder,
                           @Value("${hr.attendance.pin-max-attempts:5}") int maxAttempts,
                           @Value("${hr.attendance.pin-lockout-minutes:15}") int lockoutMinutes) {
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.maxAttempts = maxAttempts;
        this.lockout = Duration.ofMinutes(lockoutMinutes);
    }

    /** Validates and stores a new PIN (4–6 digits, not a repeated digit or a straight run). Caller checks permissions. */
    @Transactional
    public void set(EmployeeEntity employee, String pin) {
        validate(pin);
        employee.setClockPinHash(passwordEncoder.encode(pin));
        employee.setClockPinFailCount((short) 0);
        employee.setClockPinLockedUntil(null);
        employee.setClockPinSetAt(Instant.now());
        employeeRepository.save(employee);
    }

    /** Loads the employee fresh in this transaction (use after {@link #verify}, which updates the row separately). */
    @Transactional
    public EmployeeEntity setById(UUID employeeId, String pin) {
        EmployeeEntity employee = employeeRepository.findById(employeeId).orElseThrow(() -> new ApiException(404, "Employee not found"));
        set(employee, pin);
        return employee;
    }

    @Transactional
    public void clear(EmployeeEntity employee) {
        employee.setClockPinHash(null);
        employee.setClockPinFailCount((short) 0);
        employee.setClockPinLockedUntil(null);
        employee.setClockPinSetAt(null);
        employeeRepository.save(employee);
    }

    /** Checks a PIN and records the attempt. Runs in its own transaction so failures are persisted. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result verify(UUID employeeId, String pin) {
        EmployeeEntity employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null || employee.getClockPinHash() == null) {
            return Result.NOT_SET;
        }
        Instant now = Instant.now();
        if (employee.getClockPinLockedUntil() != null && employee.getClockPinLockedUntil().isAfter(now)) {
            return Result.LOCKED;
        }
        if (pin != null && passwordEncoder.matches(pin, employee.getClockPinHash())) {
            if (employee.getClockPinFailCount() != 0 || employee.getClockPinLockedUntil() != null) {
                employee.setClockPinFailCount((short) 0);
                employee.setClockPinLockedUntil(null);
            }
            return Result.OK;
        }
        short fails = (short) (employee.getClockPinFailCount() + 1);
        if (fails >= maxAttempts) {
            employee.setClockPinFailCount((short) 0);
            employee.setClockPinLockedUntil(now.plus(lockout));
            return Result.LOCKED;
        }
        employee.setClockPinFailCount(fails);
        return Result.WRONG;
    }

    /** Throws the matching API error unless the result is OK. Messages never reveal whether an employee exists. */
    public static void requireOk(Result result) {
        switch (result) {
            case OK -> { }
            case LOCKED -> throw new ApiException(423, "Too many wrong PINs. Try again later or ask a manager.");
            case NOT_SET -> throw new ApiException(401, "No clock PIN is set for this person. Ask a manager to set one.");
            default -> throw new ApiException(401, "Incorrect PIN");
        }
    }

    static void validate(String pin) {
        if (pin == null || !DIGITS.matcher(pin).matches()) {
            throw new ApiException(400, "The PIN must be 4 to 6 digits");
        }
        if (pin.chars().distinct().count() == 1) {
            throw new ApiException(400, "Choose a PIN that is not one repeated digit");
        }
        boolean ascending = true;
        boolean descending = true;
        for (int i = 1; i < pin.length(); i++) {
            int step = pin.charAt(i) - pin.charAt(i - 1);
            ascending &= step == 1;
            descending &= step == -1;
        }
        if (ascending || descending) {
            throw new ApiException(400, "Choose a PIN that is not a straight run like 1234");
        }
    }
}
