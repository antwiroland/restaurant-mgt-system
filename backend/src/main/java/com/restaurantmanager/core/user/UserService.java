package com.restaurantmanager.core.user;

import com.restaurantmanager.core.audit.AuditService;
import com.restaurantmanager.core.branch.BranchService;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.user.dto.AssignRoleRequest;
import com.restaurantmanager.core.user.dto.SetPinRequest;
import com.restaurantmanager.core.user.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final BranchService branchService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService,
                       BranchService branchService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.branchService = branchService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserResponse assignRole(UserPrincipal principal, UUID userId, AssignRoleRequest request, String ipAddress) {
        if (principal.role() != Role.ADMIN) {
            throw new ApiException(403, "Only admin can assign roles");
        }
        UserEntity target = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(404, "User not found"));
        target.setRole(request.role());
        if (request.branchId() != null) {
            target.setBranch(branchService.require(request.branchId()));
        } else if (request.role() == Role.CUSTOMER) {
            target.setBranch(null);
        }
        userRepository.save(target);

        UserEntity actor = userRepository.findById(principal.userId()).orElse(null);
        auditService.log(actor, AuditAction.ROLE_ASSIGNED, "User", target.getId(),
                "{\"newRole\":\"" + request.role().name() + "\"}", ipAddress);
        return toResponse(target);
    }

    @Transactional
    public void setPin(UserPrincipal principal, UUID userId, SetPinRequest request) {
        UserEntity target = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(404, "User not found"));
        if (target.getRole() != Role.MANAGER) {
            throw new ApiException(403, "PIN can only be set for manager accounts");
        }
        if (principal.role() == Role.MANAGER && !principal.userId().equals(userId)) {
            throw new ApiException(403, "Manager can only set own PIN");
        }
        if (principal.role() != Role.MANAGER && principal.role() != Role.ADMIN) {
            throw new ApiException(403, "Role cannot set PIN");
        }

        target.setPinHash(passwordEncoder.encode(request.pin()));
        target.setPinFailCount((short) 0);
        target.setPinLockedUntil(null);
        userRepository.save(target);
    }

    private UserResponse toResponse(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getPhone(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.getBranch() == null ? null : user.getBranch().getId(),
                user.getBranch() == null ? null : user.getBranch().getName());
    }
}
