package com.restaurantmanager.core.hr.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocumentEntity, UUID> {
    List<EmployeeDocumentEntity> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<EmployeeDocumentEntity> findByEmployeeIdAndArchivedFalseOrderByCreatedAtDesc(UUID employeeId);

    /** Current documents with an expiry date on or before {@code until} (already expired included). */
    List<EmployeeDocumentEntity> findByArchivedFalseAndExpiresOnLessThanEqualOrderByExpiresOnAsc(java.time.LocalDate until);
}
