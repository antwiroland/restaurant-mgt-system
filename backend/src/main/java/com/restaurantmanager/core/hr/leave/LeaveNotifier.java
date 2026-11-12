package com.restaurantmanager.core.hr.leave;

import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.access.HrUserPermissionRepository;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.notification.push.PushNotificationService;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Leave notifications over the existing push channel (sent after commit by {@link PushNotificationService}). Approver
 * lists are a best effort for alerting only; who may actually decide is enforced by {@link LeaveService}.
 */
@Component
public class LeaveNotifier {
    private final PushNotificationService push;
    private final UserRepository userRepository;
    private final HrUserPermissionRepository grantRepository;

    public LeaveNotifier(PushNotificationService push, UserRepository userRepository, HrUserPermissionRepository grantRepository) {
        this.push = push;
        this.userRepository = userRepository;
        this.grantRepository = grantRepository;
    }

    public void awaitingDecision(ApprovalStep step, EmployeeEntity employee, UUID branchId, String what) {
        send(approvers(step, employee, branchId), "Leave request to review", employee.fullName() + " requested " + what + ".", "LEAVE_REQUESTED");
    }

    public void decided(EmployeeEntity employee, boolean approved, String what) {
        send(self(employee),
                approved ? "Leave approved" : "Leave declined",
                "Your " + what + (approved ? " was approved." : " was declined."), "LEAVE_DECIDED");
    }

    public void cancelledByManager(EmployeeEntity employee, String what) {
        send(self(employee), "Leave cancelled",
                "Your " + what + " was cancelled by a manager.", "LEAVE_CANCELLED");
    }

    private static List<UUID> self(EmployeeEntity employee) {
        return employee.getUserId() == null ? List.of() : List.of(employee.getUserId());
    }

    private Set<UUID> approvers(ApprovalStep step, EmployeeEntity employee, UUID branchId) {
        Set<UUID> ids = new HashSet<>();
        switch (step) {
            case REPORTING_MANAGER -> {
                if (employee.getReportingManager() != null) {
                    ids.add(employee.getReportingManager().getUserId());
                }
            }
            case BRANCH_MANAGER -> userRepository.findByRoleAndActiveTrueOrderByNameAsc(Role.MANAGER).stream()
                    .filter(u -> u.getBranch() != null && u.getBranch().getId().equals(branchId))
                    .map(UserEntity::getId).forEach(ids::add);
            case HR -> {
                userRepository.findByRoleAndActiveTrueOrderByNameAsc(Role.ADMIN).forEach(u -> ids.add(u.getId()));
                List<UUID> staff = userRepository.findByRoleInAndActiveTrueOrderByNameAsc(List.of(Role.MANAGER, Role.CASHIER)).stream()
                        .map(UserEntity::getId).toList();
                grantRepository.findByUserIdIn(staff).stream()
                        .filter(g -> g.getPermission() == HrPermission.LEAVE_MANAGE)
                        .forEach(g -> ids.add(g.getUserId()));
            }
        }
        ids.remove(employee.getUserId());
        return ids;
    }

    private void send(Collection<UUID> userIds, String title, String body, String type) {
        List<UUID> recipients = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!recipients.isEmpty()) {
            push.notifyUsers(recipients, title, body, Map.of("type", type));
        }
    }
}
