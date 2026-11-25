package com.restaurantmanager.core.dining;

import com.restaurantmanager.core.order.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Requests and views of the course dining API. */
public final class DiningDtos {
    private DiningDtos() {
    }

    // ---- Course configuration -------------------------------------------------------------------------------------

    public record CourseRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 500) String description,
            @Min(0) Integer displayOrder,
            Boolean active,
            @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9 _-]*$", message = "Use letters, numbers, spaces, - or _") String defaultStation,
            /** Null: every branch (admins only). Branch managers always create courses for their own branch. */
            UUID branchId
    ) {
    }

    public record CourseActiveRequest(@NotNull Boolean active) {
    }

    /** The course ids in the order they are served. */
    public record CourseReorderRequest(@NotEmpty List<@NotNull UUID> courseIds) {
    }

    public record CourseView(UUID id, UUID branchId, String name, String description, int displayOrder, boolean active,
                             String defaultStation) {
    }

    // ---- Ordering -------------------------------------------------------------------------------------------------

    /** One dish. courseId null (or a beverage): sent to the kitchen straight away; otherwise held with its course. */
    public record SubmitItem(
            @NotNull UUID menuItemId,
            @Min(1) @Max(50) int quantity,
            @Size(max = 500) String notes,
            List<UUID> modifierOptionIds,
            UUID courseId
    ) {
    }

    public record PublicSubmitRequest(
            @NotEmpty @Size(max = 60) List<@Valid SubmitItem> items,
            @Size(max = 1000) String notes,
            @Size(max = 80) String guestName,
            @Pattern(regexp = "^\\+?[0-9 ()-]{9,20}$", message = "Enter a valid phone number") String guestPhone,
            @Size(max = 120) String sessionRef
    ) {
    }

    public record StaffSubmitRequest(
            @NotEmpty @Size(max = 100) List<@Valid SubmitItem> items,
            @Size(max = 1000) String notes
    ) {
    }

    public record NextCourseRequest(@Size(max = 120) String sessionRef) {
    }

    // ---- Views ----------------------------------------------------------------------------------------------------

    public record ItemView(UUID itemId, String name, int quantity, BigDecimal unitPrice, String notes, List<String> modifiers,
                           boolean ready) {
    }

    /** One batch sent (or held) for the kitchen: an order. */
    public record BatchView(UUID orderId, String orderNumber, OrderStatus status, String kitchenStation, Instant createdAt,
                            Instant releasedAt, BigDecimal total, List<ItemView> items) {
    }

    public record OrderCourseView(UUID id, UUID courseId, String courseName, int sequence, CourseStatus status,
                                  Instant releasedAt, String releasedByName, String releasedByRole, Instant servedAt,
                                  int itemCount, UUID pendingRequestId, List<BatchView> batches) {
    }

    public record DiningOrderView(UUID id, String number, UUID tableId, String tableNumber, UUID branchId,
                                  DiningOrderEntity.Status status, Instant openedAt, List<OrderCourseView> courses,
                                  /** Dishes and drinks of the sitting sent straight to the kitchen (no course). */
                                  List<BatchView> sentNow,
                                  /** The course a guest's "next course" would ask for, if any. */
                                  UUID nextCourseId, RequestView pendingRequest) {
    }

    public record RequestView(UUID id, UUID diningOrderId, String diningOrderNumber, UUID diningOrderCourseId,
                              String courseName, CourseStatus courseStatus, UUID tableId, String tableNumber, UUID branchId,
                              CourseReleaseRequestEntity.Status status, Instant requestedAt, UUID handledBy,
                              String handledByName, String handledByRole, Instant handledAt, List<ItemView> items) {
    }

    public record ReleaseResult(OrderCourseView course, int releasedOrders, String message) {
    }

    // ---- Guest (QR) views: no staff names, no prices of other parties ---------------------------------------------

    public record PublicItemView(String name, int quantity, List<String> modifiers, String notes) {
    }

    public record PublicCourseView(UUID id, UUID courseId, String courseName, int sequence, CourseStatus status,
                                   Instant releasedAt, List<PublicItemView> items) {
    }

    public enum NextCourseOutcome { REQUESTED, ALREADY_PENDING, NO_PENDING_COURSE, ON_HOLD }

    public record PublicRequestView(UUID id, String courseName, CourseReleaseRequestEntity.Status status, Instant requestedAt) {
    }

    public record PublicDiningView(String tableNumber, List<CourseView> courses, UUID diningOrderId,
                                   List<PublicCourseView> mealCourses, boolean canRequestNextCourse, String nextCourseName,
                                   PublicRequestView pendingRequest) {
    }

    public record NextCourseResponse(NextCourseOutcome outcome, String message, UUID requestId, String courseName) {
    }
}
