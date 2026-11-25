package com.restaurantmanager.core.dining;

import com.restaurantmanager.core.dining.DiningDtos.CourseActiveRequest;
import com.restaurantmanager.core.dining.DiningDtos.CourseReorderRequest;
import com.restaurantmanager.core.dining.DiningDtos.CourseRequest;
import com.restaurantmanager.core.dining.DiningDtos.CourseView;
import com.restaurantmanager.core.dining.DiningDtos.DiningOrderView;
import com.restaurantmanager.core.dining.DiningDtos.OrderCourseView;
import com.restaurantmanager.core.dining.DiningDtos.ReleaseResult;
import com.restaurantmanager.core.dining.DiningDtos.RequestView;
import com.restaurantmanager.core.dining.DiningDtos.StaffSubmitRequest;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Course dining for staff. Configuring courses: admins and managers. Ordering by course, releasing, holding and serving
 * courses, and handling guests' "next course" requests: floor staff (admins, managers and cashiers, who also work as
 * waiters). Kitchen progress stays on the existing KDS and order status endpoints.
 */
@RestController
@RequestMapping("/dining")
public class DiningController {
    private final DiningCourseService courseService;
    private final DiningService diningService;

    public DiningController(DiningCourseService courseService, DiningService diningService) {
        this.courseService = courseService;
        this.diningService = diningService;
    }

    // ---- Course configuration

    @GetMapping("/courses")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<CourseView> courses(@RequestParam(required = false) UUID branchId,
                                    @RequestParam(defaultValue = "false") boolean includeInactive,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        return courseService.list(branchId, includeInactive, principal);
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CourseView createCourse(@Valid @RequestBody CourseRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return courseService.create(request, principal);
    }

    @PutMapping("/courses/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CourseView updateCourse(@PathVariable UUID id, @Valid @RequestBody CourseRequest request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return courseService.update(id, request, principal);
    }

    @PatchMapping("/courses/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CourseView setCourseActive(@PathVariable UUID id, @Valid @RequestBody CourseActiveRequest request,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        return courseService.setActive(id, request.active(), principal);
    }

    @PutMapping("/courses/order")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<CourseView> reorderCourses(@Valid @RequestBody CourseReorderRequest request,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return courseService.reorder(request.courseIds(), principal);
    }

    // ---- Meals

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<DiningOrderView> openMeals(@AuthenticationPrincipal UserPrincipal principal) {
        return diningService.openMeals(principal);
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public DiningOrderView meal(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return diningService.get(id, principal);
    }

    @PostMapping("/orders/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public DiningOrderView addToMeal(@PathVariable UUID id, @Valid @RequestBody StaffSubmitRequest request,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        return diningService.submitToOrder(id, request, principal);
    }

    @GetMapping("/tables/{tableId}/active-order")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public DiningOrderView activeForTable(@PathVariable UUID tableId, @AuthenticationPrincipal UserPrincipal principal) {
        return diningService.activeForTable(tableId, principal);
    }

    /** Waiter ordering from the POS: dishes by course for a table (opens the table's meal if needed). */
    @PostMapping("/tables/{tableId}/items")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public DiningOrderView orderForTable(@PathVariable UUID tableId, @Valid @RequestBody StaffSubmitRequest request,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        return diningService.submitFromStaff(tableId, request, principal);
    }

    // ---- A course of a meal

    @PostMapping("/order-courses/{id}/release")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public ReleaseResult release(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return diningService.releaseCourse(id, principal);
    }

    @PostMapping("/order-courses/{id}/hold")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public OrderCourseView hold(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return diningService.holdCourse(id, principal);
    }

    @PostMapping("/order-courses/{id}/resume")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public OrderCourseView resume(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return diningService.resumeCourse(id, principal);
    }

    @PostMapping("/order-courses/{id}/serve")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public OrderCourseView serve(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return diningService.serveCourse(id, principal);
    }

    @PostMapping("/order-courses/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public OrderCourseView cancel(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        return diningService.cancelCourse(id, body == null ? null : body.get("reason"), principal);
    }

    // ---- Guests' "next course" requests (one request, seen by waiters and cashiers alike)

    @GetMapping("/course-release-requests")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<RequestView> requests(@AuthenticationPrincipal UserPrincipal principal) {
        return diningService.listRequests(false, principal);
    }

    @GetMapping("/course-release-requests/pending")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<RequestView> pendingRequests(@AuthenticationPrincipal UserPrincipal principal) {
        return diningService.listRequests(true, principal);
    }

    @PostMapping("/course-release-requests/{id}/release")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public ReleaseResult releaseRequest(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return diningService.releaseRequest(id, principal);
    }

    @PostMapping("/course-release-requests/{id}/dismiss")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public RequestView dismissRequest(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return diningService.dismissRequest(id, principal);
    }
}
