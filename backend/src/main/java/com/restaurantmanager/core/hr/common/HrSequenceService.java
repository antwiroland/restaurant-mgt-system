package com.restaurantmanager.core.hr.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Human-readable sequential numbers (EMP-000001, CTR-000001). The counter row is locked for the duration of the caller's
 * transaction, so concurrent creations get distinct numbers on both PostgreSQL and H2. A rolled-back transaction also rolls
 * back its increment, so numbers stay gap-free in the common case.
 */
@Service
public class HrSequenceService {
    private final HrSequenceRepository repository;

    public HrSequenceService(HrSequenceRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(String sequence, String prefix) {
        HrSequenceEntity row = repository.lockByName(sequence).orElseGet(() -> create(sequence));
        long value = row.getNextValue();
        row.setNextValue(value + 1);
        return format(prefix, value);
    }

    static String format(String prefix, long value) {
        return prefix + "-" + String.format("%06d", value);
    }

    private HrSequenceEntity create(String sequence) {
        try {
            repository.saveAndFlush(new HrSequenceEntity(sequence, 1));
        } catch (DataIntegrityViolationException ignored) {
            // another transaction created it first; fall through and lock the existing row
        }
        return repository.lockByName(sequence).orElseThrow();
    }
}
