package com.restaurantmanager.core.hr.access;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Granular HR permissions. Stored by enum name in {@code hr_user_permissions}; exposed to clients by {@link #code()}.
 * Effective permissions are the role defaults ({@link HrAccessService}) plus per-user grants.
 */
public enum HrPermission {
    EMPLOYEE_VIEW("hr.employee.view", "View employee records in your branches"),
    EMPLOYEE_CREATE("hr.employee.create", "Register employees"),
    EMPLOYEE_UPDATE("hr.employee.update", "Edit employee records"),
    EMPLOYEE_ARCHIVE("hr.employee.archive", "Record resignations, terminations and retirements"),
    EMPLOYEE_TRANSFER("hr.employee.transfer", "Move employees between branches"),
    DEPARTMENT_MANAGE("hr.department.manage", "Manage departments"),
    POSITION_MANAGE("hr.position.manage", "Manage positions"),
    CONTRACT_VIEW("hr.contract.view", "View employment contracts"),
    CONTRACT_MANAGE("hr.contract.manage", "Create, renew and end contracts"),
    DOCUMENT_VIEW("hr.document.view", "View and download employee documents"),
    DOCUMENT_MANAGE("hr.document.manage", "Upload and archive employee documents"),
    COMPENSATION_VIEW("hr.compensation.view", "See salary and wage figures"),
    BRANCH_VIEW("hr.branch.view", "View branch workforce"),
    BRANCH_ASSIGN("hr.branch.assign", "Assign employees to additional branches"),
    BRANCH_ALL("hr.branch.all", "Access HR data for every branch"),
    SCHEDULE_VIEW("hr.schedule.view", "View staff schedules"),
    SCHEDULE_MANAGE("hr.schedule.manage", "Build staff schedules"),
    SCHEDULE_PUBLISH("hr.schedule.publish", "Publish schedules"),
    SHIFT_SWAP_APPROVE("hr.shift.swap.approve", "Approve shift swaps"),
    ATTENDANCE_VIEW("hr.attendance.view", "View attendance"),
    ATTENDANCE_MANAGE("hr.attendance.manage", "Record attendance for others"),
    ATTENDANCE_CORRECT("hr.attendance.correct", "Correct attendance records"),
    TIMESHEET_REVIEW("hr.timesheet.review", "Review and approve timesheets"),
    OVERTIME_APPROVE("hr.overtime.approve", "Approve overtime"),
    LEAVE_APPROVE("hr.leave.approve", "Approve leave requests"),
    LEAVE_MANAGE("hr.leave.manage", "Configure leave types, policies and balances"),
    PAYROLL_VIEW("hr.payroll.view", "View payroll"),
    PAYROLL_PROCESS("hr.payroll.process", "Prepare and calculate payroll"),
    PAYROLL_APPROVE("hr.payroll.approve", "Approve payroll"),
    PAYROLL_FINALIZE("hr.payroll.finalize", "Finalize payroll"),
    PAYROLL_PAY("hr.payroll.pay", "Record payroll payments"),
    TIPS_MANAGE("hr.tips.manage", "Manage tip pools and allocations"),
    SERVICE_CHARGE_MANAGE("hr.service_charge.manage", "Manage service charge distribution"),
    RECRUITMENT_MANAGE("hr.recruitment.manage", "Manage vacancies and applicants"),
    PERFORMANCE_MANAGE("hr.performance.manage", "Manage performance reviews"),
    TRAINING_MANAGE("hr.training.manage", "Manage training and certifications"),
    REPORTS_VIEW("hr.reports.view", "View HR reports"),
    SETTINGS_MANAGE("hr.settings.manage", "Manage HR settings");

    private final String code;
    private final String description;

    HrPermission(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    public static Optional<HrPermission> fromCode(String value) {
        return Arrays.stream(values()).filter(p -> p.code.equals(value) || p.name().equals(value)).findFirst();
    }

    /**
     * Operational defaults for the MANAGER role (always limited to their branches). Compensation, payroll, exits, transfers
     * and configuration are not included; hiring is, because managers already register employees.
     */
    static final Set<HrPermission> MANAGER_DEFAULTS = EnumSet.of(
            EMPLOYEE_VIEW, EMPLOYEE_CREATE, EMPLOYEE_UPDATE,
            CONTRACT_VIEW, DOCUMENT_VIEW, BRANCH_VIEW,
            SCHEDULE_VIEW, SCHEDULE_MANAGE, SCHEDULE_PUBLISH, SHIFT_SWAP_APPROVE,
            ATTENDANCE_VIEW, ATTENDANCE_MANAGE, ATTENDANCE_CORRECT,
            TIMESHEET_REVIEW, OVERTIME_APPROVE, LEAVE_APPROVE, RECRUITMENT_MANAGE, REPORTS_VIEW);
}
