package com.restaurantmanager.core.hr.access;

import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/hr")
public class HrAccessController {
    private static final Set<Role> STAFF_ROLES = EnumSet.of(Role.MANAGER, Role.CASHIER, Role.RIDER);

    private final HrAccessService accessService;
    private final HrUserPermissionRepository grantRepository;
    private final HrUserBranchAccessRepository branchAccessRepository;
    private final com.restaurantmanager.core.branch.BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final HrAuditService audit;

    public HrAccessController(HrAccessService accessService, HrUserPermissionRepository grantRepository,
                              HrUserBranchAccessRepository branchAccessRepository,
                              com.restaurantmanager.core.branch.BranchRepository branchRepository, UserRepository userRepository,
                              EmployeeRepository employeeRepository, HrAuditService audit) {
        this.accessService = accessService;
        this.grantRepository = grantRepository;
        this.branchAccessRepository = branchAccessRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.audit = audit;
    }

    /** What the signed-in user may do in HR; drives navigation and button visibility in the web app. */
    @GetMapping("/me/access")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','RIDER')")
    @Transactional(readOnly = true)
    public MyAccess myAccess(@AuthenticationPrincipal UserPrincipal principal) {
        HrAccessService.HrAccess access = accessService.access(principal);
        UUID employeeId = employeeRepository.findByUserId(access.userId()).map(e -> e.getId()).orElse(null);
        return new MyAccess(access.permissions().stream().map(HrPermission::code).collect(Collectors.toCollection(TreeSet::new)),
                access.allBranches(), access.branchIds(), employeeId);
    }

    @GetMapping("/access/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PermissionView> permissions() {
        return Arrays.stream(HrPermission.values())
                .map(p -> new PermissionView(p.code(), p.description(), HrPermission.MANAGER_DEFAULTS.contains(p)))
                .toList();
    }

    /** Staff accounts (non-admin) with their extra HR grants. Admins already hold every HR permission. */
    @GetMapping("/access/grants")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<UserGrants> grants() {
        List<UserEntity> users = userRepository.findByRoleInAndActiveTrueOrderByNameAsc(STAFF_ROLES);
        List<UUID> ids = users.stream().map(UserEntity::getId).toList();
        Map<UUID, List<HrUserPermissionEntity>> byUser = grantRepository.findByUserIdIn(ids)
                .stream().collect(Collectors.groupingBy(HrUserPermissionEntity::getUserId));
        Map<UUID, List<HrUserBranchAccessEntity>> branchesByUser = branchAccessRepository.findByUserIdIn(ids)
                .stream().collect(Collectors.groupingBy(HrUserBranchAccessEntity::getUserId));
        return users.stream().map(u -> toGrants(u, byUser.getOrDefault(u.getId(), List.of()),
                branchesByUser.getOrDefault(u.getId(), List.of()))).toList();
    }

    @PutMapping("/access/grants/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserGrants replaceGrants(@PathVariable UUID userId, @Valid @RequestBody GrantRequest request,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new ApiException(404, "User not found"));
        if (!STAFF_ROLES.contains(user.getRole())) {
            throw new ApiException(400, user.getRole() == Role.ADMIN
                    ? "Admins already hold every HR permission"
                    : "HR permissions can only be granted to staff accounts");
        }
        Set<HrPermission> requested = EnumSet.noneOf(HrPermission.class);
        for (String code : request.permissions()) {
            requested.add(HrPermission.fromCode(code).orElseThrow(() -> new ApiException(400, "Unknown permission " + code)));
        }
        List<HrUserPermissionEntity> existing = grantRepository.findByUserId(userId);
        Set<HrPermission> before = existing.stream().map(HrUserPermissionEntity::getPermission)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(HrPermission.class)));
        grantRepository.deleteAll(existing.stream().filter(g -> !requested.contains(g.getPermission())).toList());
        requested.stream().filter(p -> !before.contains(p))
                .forEach(p -> grantRepository.save(new HrUserPermissionEntity(userId, p, principal.userId())));
        grantRepository.flush();
        Set<String> added = requested.stream().filter(p -> !before.contains(p)).map(HrPermission::code).collect(Collectors.toCollection(TreeSet::new));
        Set<String> removed = before.stream().filter(p -> !requested.contains(p)).map(HrPermission::code).collect(Collectors.toCollection(TreeSet::new));
        if (!added.isEmpty() || !removed.isEmpty()) {
            audit.log(principal.userId(), AuditAction.HR_PERMISSIONS_CHANGED, "User", userId, Map.of("added", added, "removed", removed));
        }
        return toGrants(user, grantRepository.findByUserId(userId), branchAccessRepository.findByUserId(userId));
    }

    /**
     * Replaces the extra branches in a staff account's HR scope (in addition to the branch on their account). Used for
     * managers covering more than one branch without giving them every branch.
     */
    @PutMapping("/access/branches/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserGrants replaceBranches(@PathVariable UUID userId, @Valid @RequestBody BranchScopeRequest request,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new ApiException(404, "User not found"));
        if (!STAFF_ROLES.contains(user.getRole())) {
            throw new ApiException(400, user.getRole() == Role.ADMIN
                    ? "Admins already see every branch"
                    : "Branch access can only be granted to staff accounts");
        }
        Set<UUID> requested = new java.util.HashSet<>(request.branchIds());
        if (user.getBranch() != null) {
            requested.remove(user.getBranch().getId());
        }
        if (branchRepository.findAllById(requested).size() != requested.size()) {
            throw new ApiException(404, "Branch not found");
        }
        List<HrUserBranchAccessEntity> existing = branchAccessRepository.findByUserId(userId);
        Set<UUID> before = existing.stream().map(HrUserBranchAccessEntity::getBranchId).collect(Collectors.toSet());
        branchAccessRepository.deleteAll(existing.stream().filter(a -> !requested.contains(a.getBranchId())).toList());
        requested.stream().filter(b -> !before.contains(b))
                .forEach(b -> branchAccessRepository.save(new HrUserBranchAccessEntity(userId, b, principal.userId())));
        branchAccessRepository.flush();
        Set<String> added = requested.stream().filter(b -> !before.contains(b)).map(UUID::toString).collect(Collectors.toCollection(TreeSet::new));
        Set<String> removed = before.stream().filter(b -> !requested.contains(b)).map(UUID::toString).collect(Collectors.toCollection(TreeSet::new));
        if (!added.isEmpty() || !removed.isEmpty()) {
            audit.log(principal.userId(), AuditAction.HR_BRANCH_ACCESS_CHANGED, "User", userId, Map.of("added", added, "removed", removed));
        }
        return toGrants(user, grantRepository.findByUserId(userId), branchAccessRepository.findByUserId(userId));
    }

    private static UserGrants toGrants(UserEntity u, List<HrUserPermissionEntity> grants, List<HrUserBranchAccessEntity> branches) {
        BranchEntity branch = u.getBranch();
        return new UserGrants(u.getId(), u.getName(), u.getRole().name(), branch == null ? null : branch.getId(),
                branch == null ? null : branch.getName(),
                HrAccessService.defaultsFor(u.getRole()).stream().map(HrPermission::code).collect(Collectors.toCollection(TreeSet::new)),
                grants.stream().map(g -> g.getPermission().code()).collect(Collectors.toCollection(TreeSet::new)),
                branches.stream().map(HrUserBranchAccessEntity::getBranchId).collect(Collectors.toSet()));
    }

    public record MyAccess(Set<String> permissions, boolean allBranches, Set<UUID> branchIds, UUID employeeId) {}

    public record PermissionView(String code, String description, boolean managerDefault) {}

    public record UserGrants(UUID userId, String name, String role, UUID branchId, String branchName,
                             Set<String> rolePermissions, Set<String> grantedPermissions, Set<UUID> extraBranchIds) {}

    public record GrantRequest(@NotNull Set<String> permissions) {}

    public record BranchScopeRequest(@NotNull Set<UUID> branchIds) {}
}
