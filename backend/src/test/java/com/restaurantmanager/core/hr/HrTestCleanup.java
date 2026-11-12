package com.restaurantmanager.core.hr;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Removes HR rows between integration tests, in foreign-key order (HR tables reference users and branches). */
@Component
public class HrTestCleanup {
    private final EntityManager entityManager;

    public HrTestCleanup(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** Full clean for H2 tests, where nothing is seeded. */
    @Transactional
    public void clean() {
        cleanEmployees();
        entityManager.createQuery("delete from ShiftTemplateEntity").executeUpdate();
        entityManager.createQuery("delete from SchedulingPolicyEntity").executeUpdate();
        entityManager.createQuery("delete from AttendancePolicyEntity").executeUpdate();
        entityManager.createQuery("delete from LeavePolicyEntity").executeUpdate();
        entityManager.createQuery("delete from LeaveTypeEntity").executeUpdate();
        entityManager.createQuery("delete from OvertimeRuleEntity").executeUpdate();
        entityManager.createQuery("delete from PublicHolidayEntity").executeUpdate();
        entityManager.createQuery("delete from PositionEntity").executeUpdate();
        entityManager.createQuery("delete from DepartmentEntity").executeUpdate();
        entityManager.createQuery("delete from HrSequenceEntity").executeUpdate();
    }

    /** Keeps migration-seeded departments, positions and sequences (PostgreSQL/Flyway tests). */
    @Transactional
    public void cleanEmployees() {
        entityManager.createQuery("delete from LeaveTransactionEntity").executeUpdate();
        entityManager.createQuery("delete from LeaveBalanceEntity").executeUpdate();
        entityManager.createQuery("delete from LeaveApprovalEntity").executeUpdate();
        entityManager.createQuery("delete from LeaveRequestEntity").executeUpdate();
        entityManager.createQuery("delete from TimesheetLineEntity").executeUpdate();
        entityManager.createQuery("delete from TimesheetEntity").executeUpdate();
        entityManager.createQuery("delete from OvertimeRequestEntity").executeUpdate();
        entityManager.createQuery("delete from AttendanceEventEntity").executeUpdate();
        entityManager.createQuery("delete from AttendanceCorrectionEntity").executeUpdate();
        entityManager.createQuery("delete from AttendanceBreakEntity").executeUpdate();
        entityManager.createQuery("update TimeEntryEntity t set t.employeeId = null").executeUpdate();
        entityManager.createQuery("delete from TimeEntryEntity t where t.userId is null").executeUpdate();
        entityManager.createQuery("delete from SwapRequestEntity").executeUpdate();
        entityManager.createQuery("delete from AvailabilityEntity").executeUpdate();
        entityManager.createQuery("update WorkScheduleEntity s set s.employeeId = null, s.templateId = null").executeUpdate();
        entityManager.createQuery("delete from BranchAssignmentEntity").executeUpdate();
        entityManager.createQuery("delete from HrUserBranchAccessEntity").executeUpdate();
        entityManager.createQuery("delete from ContractEntity").executeUpdate();
        entityManager.createQuery("delete from EmployeeDocumentEntity").executeUpdate();
        entityManager.createQuery("delete from EmergencyContactEntity").executeUpdate();
        entityManager.createQuery("update DepartmentEntity d set d.headEmployeeId = null").executeUpdate();
        entityManager.createQuery("update EmployeeEntity e set e.reportingManager = null").executeUpdate();
        entityManager.createQuery("delete from EmployeeEntity").executeUpdate();
        entityManager.createQuery("delete from HrUserPermissionEntity").executeUpdate();
    }
}
