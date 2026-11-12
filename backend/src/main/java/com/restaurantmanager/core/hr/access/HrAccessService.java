package com.restaurantmanager.core.hr.access;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Single place that decides what an authenticated user may do in HR and which branches' data they may see.
 *
 * <p>Branch rule: ADMIN, or anyone granted {@link HrPermission#BRANCH_ALL}, sees every branch. Everyone else sees only the
 * branch on their user account plus any extra branches an admin added ({@code hr_user_branch_access}); with none they see
 * no branch-specific HR data. This is deliberately stricter than
 * the rest of the app, where a MANAGER without a branch sees everything.</p>
 */
@Service
public class HrAccessService {
    private final HrUserPermissionRepository grantRepository;
    private final HrUserBranchAccessRepository branchAccessRepository;
    private final UserRepository userRepository;

    public HrAccessService(HrUserPermissionRepository grantRepository, HrUserBranchAccessRepository branchAccessRepository,
                           UserRepository userRepository) {
        this.grantRepository = grantRepository;
        this.branchAccessRepository = branchAccessRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public HrAccess access(UserPrincipal principal) {
        if (principal == null) {
            throw new ApiException(401, "Unauthorized");
        }
        UserEntity user = userRepository.findById(principal.userId())
                .filter(UserEntity::isActive)
                .orElseThrow(() -> new ApiException(401, "Unauthorized"));
        Role role = user.getRole();
        UUID branchId = user.getBranch() == null ? null : user.getBranch().getId();
        Set<HrPermission> permissions = EnumSet.noneOf(HrPermission.class);
        permissions.addAll(defaultsFor(role));
        if (role != Role.CUSTOMER && role != Role.ADMIN) {
            grantRepository.findByUserId(user.getId()).forEach(g -> permissions.add(g.getPermission()));
        }
        // central HR (an HR login without a branch) covers the organisation; a branch HR officer covers their branch
        boolean allBranches = role == Role.ADMIN || (role == Role.HR && branchId == null) || permissions.contains(HrPermission.BRANCH_ALL);
        Set<UUID> branches = new java.util.HashSet<>();
        if (branchId != null) {
            branches.add(branchId);
        }
        if (!allBranches && role != Role.CUSTOMER) {
            branchAccessRepository.findByUserId(user.getId()).forEach(a -> branches.add(a.getBranchId()));
        }
        return new HrAccess(user.getId(), role, Collections.unmodifiableSet(permissions), allBranches, Collections.unmodifiableSet(branches));
    }

    /** Loads access and fails with 403 unless the user holds every listed permission. */
    public HrAccess require(UserPrincipal principal, HrPermission... required) {
        HrAccess access = access(principal);
        for (HrPermission permission : required) {
            if (!access.has(permission)) {
                throw new ApiException(403, "You do not have permission to do this");
            }
        }
        return access;
    }

    /** Loads access and fails with 403 unless the user holds at least one listed permission. */
    public HrAccess requireAny(UserPrincipal principal, HrPermission... anyOf) {
        HrAccess access = access(principal);
        for (HrPermission permission : anyOf) {
            if (access.has(permission)) {
                return access;
            }
        }
        throw new ApiException(403, "You do not have permission to do this");
    }

    public static Set<HrPermission> defaultsFor(Role role) {
        return switch (role) {
            case ADMIN -> EnumSet.allOf(HrPermission.class);
            case MANAGER -> EnumSet.copyOf(HrPermission.MANAGER_DEFAULTS);
            case HR -> EnumSet.copyOf(HrPermission.HR_DEFAULTS);
            default -> EnumSet.noneOf(HrPermission.class);
        };
    }

    public record HrAccess(UUID userId, Role role, Set<HrPermission> permissions, boolean allBranches, Set<UUID> branchIds) {
        public boolean has(HrPermission permission) {
            return permissions.contains(permission);
        }

        /** Whether data belonging to {@code branchId} is visible. Null branch = organisation-level, central users only. */
        public boolean canSeeBranch(UUID branchId) {
            return allBranches || (branchId != null && branchIds.contains(branchId));
        }

        /** For writes that target a branch: 403 when out of scope. */
        public void assertBranch(UUID branchId) {
            if (!canSeeBranch(branchId)) {
                throw new ApiException(403, "You do not have access to this branch");
            }
        }

        /** Branch filter for queries, or null when every branch is visible. */
        public Set<UUID> branchFilter() {
            return allBranches ? null : branchIds;
        }
    }
}
