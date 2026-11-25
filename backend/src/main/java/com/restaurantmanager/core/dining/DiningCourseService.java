package com.restaurantmanager.core.dining;

import com.restaurantmanager.core.audit.AuditService;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.dining.DiningDtos.CourseRequest;
import com.restaurantmanager.core.dining.DiningDtos.CourseView;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The courses a restaurant serves. Admins manage restaurant-wide courses (no branch) and any branch's own; a branch
 * manager manages only their branch's own courses. Course names are never used in logic: everything goes by id and
 * display order, and meals keep a copy of the name they were ordered under.
 */
@Service
public class DiningCourseService {
    private final DiningCourseRepository repository;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public DiningCourseService(DiningCourseRepository repository, AuditService auditService, UserRepository userRepository,
                               com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    /** Courses for staff screens. Branch staff see their branch's; admins see one branch's, or every course. */
    @Transactional(readOnly = true)
    public List<CourseView> list(UUID branchId, boolean includeInactive, UserPrincipal principal) {
        UUID scoped = scopedBranch(principal);
        if (scoped != null && branchId != null && !scoped.equals(branchId)) {
            throw new ApiException(403, "Forbidden");
        }
        UUID effective = scoped != null ? scoped : branchId;
        List<DiningCourseEntity> courses = effective == null && principal.role() == Role.ADMIN && branchId == null
                ? repository.findAllOrdered()
                : repository.findForBranch(effective);
        return courses.stream().filter(c -> includeInactive || c.isActive()).map(DiningCourseService::toView).toList();
    }

    /** Active courses a table at this branch can order (restaurant-wide plus the branch's own), in serving order. */
    @Transactional(readOnly = true)
    public List<DiningCourseEntity> offeredAt(UUID branchId) {
        return repository.findForBranch(branchId).stream().filter(DiningCourseEntity::isActive).toList();
    }

    @Transactional
    public CourseView create(CourseRequest request, UserPrincipal principal) {
        UUID scoped = scopedBranch(principal);
        UUID branchId = scoped != null ? scoped : request.branchId();
        if (branchId == null && principal.role() != Role.ADMIN) {
            throw new ApiException(403, "Only an admin can add a course for every branch");
        }
        assertNameFree(request.name(), branchId, null);
        DiningCourseEntity course = new DiningCourseEntity();
        course.setBranchId(branchId);
        course.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : nextDisplayOrder(branchId));
        apply(course, request);
        DiningCourseEntity saved = repository.save(course);
        audit(principal, saved.getId(), "created " + saved.getName());
        return toView(saved);
    }

    @Transactional
    public CourseView update(UUID id, CourseRequest request, UserPrincipal principal) {
        DiningCourseEntity course = editable(id, principal);
        assertNameFree(request.name(), course.getBranchId(), id);
        if (request.displayOrder() != null) {
            course.setDisplayOrder(request.displayOrder());
        }
        apply(course, request);
        DiningCourseEntity saved = repository.save(course);
        audit(principal, id, "updated " + saved.getName());
        return toView(saved);
    }

    @Transactional
    public CourseView setActive(UUID id, boolean active, UserPrincipal principal) {
        DiningCourseEntity course = editable(id, principal);
        course.setActive(active);
        DiningCourseEntity saved = repository.save(course);
        audit(principal, id, (active ? "activated " : "deactivated ") + saved.getName());
        return toView(saved);
    }

    /** Sets the serving order: the courses in the given order get display order 10, 20, 30... */
    @Transactional
    public List<CourseView> reorder(List<UUID> courseIds, UserPrincipal principal) {
        if (new HashSet<>(courseIds).size() != courseIds.size()) {
            throw new ApiException(400, "Each course may appear only once");
        }
        Map<UUID, DiningCourseEntity> byId = repository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(DiningCourseEntity::getId, Function.identity()));
        List<DiningCourseEntity> ordered = new ArrayList<>();
        for (UUID id : courseIds) {
            DiningCourseEntity course = byId.get(id);
            if (course == null) {
                throw new ApiException(404, "Course not found");
            }
            assertEditable(course, principal);
            ordered.add(course);
        }
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setDisplayOrder((i + 1) * 10);
        }
        repository.saveAll(ordered);
        audit(principal, null, "reordered " + ordered.stream().map(DiningCourseEntity::getName).collect(Collectors.joining(" > ")));
        return ordered.stream().map(DiningCourseService::toView).toList();
    }

    private DiningCourseEntity editable(UUID id, UserPrincipal principal) {
        DiningCourseEntity course = repository.findById(id).orElseThrow(() -> new ApiException(404, "Course not found"));
        assertEditable(course, principal);
        return course;
    }

    private void assertEditable(DiningCourseEntity course, UserPrincipal principal) {
        if (principal.role() == Role.ADMIN) {
            return;
        }
        UUID scoped = scopedBranch(principal);
        if (course.getBranchId() == null) {
            if (scoped != null) {
                throw new ApiException(403, "Only an admin can change a course offered at every branch");
            }
            return;
        }
        if (scoped != null && !scoped.equals(course.getBranchId())) {
            throw new ApiException(403, "Forbidden");
        }
    }

    private void assertNameFree(String name, UUID branchId, UUID exceptId) {
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        boolean taken = repository.findAll().stream()
                .filter(c -> !c.getId().equals(exceptId))
                .filter(c -> Objects.equals(c.getBranchId(), branchId))
                .anyMatch(c -> c.getName().trim().toLowerCase(Locale.ROOT).equals(wanted));
        if (taken) {
            throw new ApiException(409, "There is already a course called " + name.trim());
        }
    }

    private int nextDisplayOrder(UUID branchId) {
        return repository.findForBranch(branchId).stream().mapToInt(DiningCourseEntity::getDisplayOrder).max().orElse(0) + 10;
    }

    private static void apply(DiningCourseEntity course, CourseRequest request) {
        course.setName(request.name().trim());
        course.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
        if (request.active() != null) {
            course.setActive(request.active());
        }
        course.setDefaultStation(normalizeStation(request.defaultStation()));
    }

    /** KITCHEN, BAR, COLD_KITCHEN...: upper case with underscores; blank means the main kitchen (null). */
    static String normalizeStation(String station) {
        if (station == null) {
            return null;
        }
        String normalized = station.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        return normalized.isEmpty() ? null : normalized;
    }

    private void audit(UserPrincipal principal, UUID courseId, String what) {
        auditService.log(userRepository.findById(principal.userId()).orElse(null), AuditAction.COURSE_CONFIG_CHANGED,
                "DINING_COURSE", courseId, json(objectMapper, Map.of("change", what)), null);
    }

    static String json(com.fasterxml.jackson.databind.ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return null;
        }
    }

    static UUID scopedBranch(UserPrincipal principal) {
        boolean scoped = principal != null && (principal.role() == Role.MANAGER || principal.role() == Role.CASHIER)
                && principal.branchId() != null;
        return scoped ? principal.branchId() : null;
    }

    static CourseView toView(DiningCourseEntity course) {
        return new CourseView(course.getId(), course.getBranchId(), course.getName(), course.getDescription(),
                course.getDisplayOrder(), course.isActive(), course.getDefaultStation());
    }
}
