package com.restaurantmanager.core.dining;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantmanager.core.audit.AuditService;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.dining.DiningDtos.BatchView;
import com.restaurantmanager.core.dining.DiningDtos.DiningOrderView;
import com.restaurantmanager.core.dining.DiningDtos.ItemView;
import com.restaurantmanager.core.dining.DiningDtos.NextCourseOutcome;
import com.restaurantmanager.core.dining.DiningDtos.NextCourseResponse;
import com.restaurantmanager.core.dining.DiningDtos.OrderCourseView;
import com.restaurantmanager.core.dining.DiningDtos.PublicCourseView;
import com.restaurantmanager.core.dining.DiningDtos.PublicDiningView;
import com.restaurantmanager.core.dining.DiningDtos.PublicItemView;
import com.restaurantmanager.core.dining.DiningDtos.PublicRequestView;
import com.restaurantmanager.core.dining.DiningDtos.ReleaseResult;
import com.restaurantmanager.core.dining.DiningDtos.RequestView;
import com.restaurantmanager.core.dining.DiningDtos.SubmitItem;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.menu.MenuItemRepository;
import com.restaurantmanager.core.order.DiningPlacement;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderItemEntity;
import com.restaurantmanager.core.order.OrderItemModifierEntity;
import com.restaurantmanager.core.order.OrderItemModifierRepository;
import com.restaurantmanager.core.order.OrderItemRepository;
import com.restaurantmanager.core.order.OrderRealtimePublisher;
import com.restaurantmanager.core.order.OrderRepository;
import com.restaurantmanager.core.order.OrderService;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.order.OrderStatusChangedEvent;
import com.restaurantmanager.core.order.OrderType;
import com.restaurantmanager.core.order.dto.OrderCancelRequest;
import com.restaurantmanager.core.order.dto.OrderCreateItemRequest;
import com.restaurantmanager.core.order.dto.OrderCreateRequest;
import com.restaurantmanager.core.order.dto.OrderStatusUpdateRequest;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.RestaurantTableRepository;
import com.restaurantmanager.core.table.TableSittingEndedEvent;
import com.restaurantmanager.core.table.TableSittingService;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Course dining: a party orders its meal in courses from the table QR page (or a waiter does it from the POS), and a
 * waiter, cashier or manager releases each course to the kitchen when the table is ready for it.
 *
 * <p>Every batch of dishes is an ordinary order created through {@link OrderService}, so prices, discounts, taxes,
 * stock, the table's running bill, payments and receipts work as for any order. A course's batches are created HELD
 * (on the bill, not on the KDS); releasing the course moves them to PENDING in one conditional update under a row
 * lock, so two people releasing at once send the dishes once. Beverages and dishes without a course go straight to
 * the kitchen. Dishes added to a course that was already released also go straight to the kitchen, as a new batch, so
 * nothing already sent is sent again.
 */
@Service
public class DiningService {
    /** Staff see guests' course requests from the last few hours. */
    private static final Duration REQUEST_WINDOW = Duration.ofHours(12);

    private final DiningOrderRepository diningOrders;
    private final DiningOrderCourseRepository orderCourses;
    private final CourseReleaseRequestRepository requests;
    private final CourseStatusHistoryRepository history;
    private final DiningCourseService courseService;
    private final DiningNotifier notifier;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemModifierRepository modifierRepository;
    private final OrderRealtimePublisher orderRealtime;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantTableRepository tableRepository;
    private final TableSittingService tableSitting;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public DiningService(DiningOrderRepository diningOrders, DiningOrderCourseRepository orderCourses,
                         CourseReleaseRequestRepository requests, CourseStatusHistoryRepository history,
                         DiningCourseService courseService, DiningNotifier notifier, OrderService orderService,
                         OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                         OrderItemModifierRepository modifierRepository, OrderRealtimePublisher orderRealtime,
                         MenuItemRepository menuItemRepository, RestaurantTableRepository tableRepository,
                         TableSittingService tableSitting, UserRepository userRepository, AuditService auditService,
                         ObjectMapper objectMapper) {
        this.diningOrders = diningOrders;
        this.orderCourses = orderCourses;
        this.requests = requests;
        this.history = history;
        this.courseService = courseService;
        this.notifier = notifier;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.modifierRepository = modifierRepository;
        this.orderRealtime = orderRealtime;
        this.menuItemRepository = menuItemRepository;
        this.tableRepository = tableRepository;
        this.tableSitting = tableSitting;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    // =================================================================================================================
    // Ordering
    // =================================================================================================================

    /** A guest orders from the table QR page: dishes for later courses are held, drinks and "now" dishes are sent. */
    @Transactional
    public PublicDiningView submitFromTable(String tableToken, DiningDtos.PublicSubmitRequest request) {
        RestaurantTableEntity table = lockTable(tableByToken(tableToken).getId());
        submit(table, request.items(), request.notes(), null, request.guestName(), request.guestPhone());
        return publicView(table);
    }

    /** A waiter (cashier role), manager or admin adds dishes for a table, by course, from the POS. */
    @Transactional
    public DiningOrderView submitFromStaff(UUID tableId, DiningDtos.StaffSubmitRequest request, UserPrincipal principal) {
        RestaurantTableEntity existing = tableRepository.findById(tableId).orElseThrow(() -> new ApiException(404, "Table not found"));
        assertBranch(existing.getBranch() == null ? null : existing.getBranch().getId(), principal);
        RestaurantTableEntity table = lockTable(tableId);
        DiningOrderEntity dining = submit(table, request.items(), request.notes(), principal, null, null);
        return view(dining);
    }

    /** Staff add dishes to a known meal (same as adding them for its table while the meal is open). */
    @Transactional
    public DiningOrderView submitToOrder(UUID diningOrderId, DiningDtos.StaffSubmitRequest request, UserPrincipal principal) {
        DiningOrderEntity dining = diningOrders.findById(diningOrderId).orElseThrow(() -> new ApiException(404, "Meal not found"));
        assertBranch(dining.getBranchId(), principal);
        if (dining.getStatus() != DiningOrderEntity.Status.OPEN) {
            throw new ApiException(409, "This meal is closed. Start a new order for the table");
        }
        return submitFromStaff(dining.getTable().getId(), request, principal);
    }

    private DiningOrderEntity submit(RestaurantTableEntity table, List<SubmitItem> items, String notes, UserPrincipal principal,
                                     String guestName, String guestPhone) {
        UUID branchId = table.getBranch() == null ? null : table.getBranch().getId();
        Map<UUID, DiningCourseEntity> offered = new LinkedHashMap<>();
        courseService.offeredAt(branchId).forEach(course -> offered.put(course.getId(), course));

        // Group the dishes into batches: one per (course, station). Beverages never wait for a course.
        record BatchKey(UUID courseId, String station) {}
        Map<BatchKey, List<OrderCreateItemRequest>> batches = new LinkedHashMap<>();
        for (SubmitItem item : items) {
            MenuItemEntity menuItem = menuItemRepository.findById(item.menuItemId())
                    .orElseThrow(() -> new ApiException(404, "Menu item not found"));
            CategoryEntity category = menuItem.getCategory();
            UUID courseId = category.isBeverage() ? null : item.courseId();
            if (courseId != null && !offered.containsKey(courseId)) {
                throw new ApiException(400, "That course is not offered here. Choose another course");
            }
            String station = category.getKitchenStation() != null ? category.getKitchenStation()
                    : courseId == null ? null : offered.get(courseId).getDefaultStation();
            batches.computeIfAbsent(new BatchKey(courseId, station), key -> new ArrayList<>())
                    .add(new OrderCreateItemRequest(item.menuItemId(), item.quantity(), item.notes(), item.modifierOptionIds()));
        }

        DiningOrderEntity dining = activeOrOpen(table);
        Set<DiningOrderCourseEntity> touched = new HashSet<>();
        for (Map.Entry<BatchKey, List<OrderCreateItemRequest>> batch : batches.entrySet()) {
            DiningOrderCourseEntity course = batch.getKey().courseId() == null ? null
                    : courseFor(dining, offered.get(batch.getKey().courseId()), principal);
            boolean held = course != null && !course.isReleased();
            DiningPlacement placement = new DiningPlacement(dining.getId(), course == null ? null : course.getId(),
                    batch.getKey().station(), held, course != null && course.isReleased() ? course.getReleasedBy() : null);
            OrderCreateRequest create = new OrderCreateRequest(OrderType.DINE_IN, table.getId(), null, batch.getValue(),
                    null, null, null, null, notes, null, null);
            orderService.createDiningOrder(create, principal, guestName, guestPhone, placement);
            if (course != null) {
                touched.add(course);
            }
        }
        for (DiningOrderCourseEntity course : touched) {
            recompute(course, principal, "Dishes added");
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("diningOrderId", dining.getId());
        details.put("tableNumber", table.getNumber());
        notifier.staff("DINING_ORDER_UPDATED", dining.getBranchId(), details);
        notifier.guest("DINING_ORDER_UPDATED", table.getQrToken(), Map.of());
        return dining;
    }

    /** The meal's course for a configured course, created (with a copy of the name) the first time it is ordered. */
    private DiningOrderCourseEntity courseFor(DiningOrderEntity dining, DiningCourseEntity course, UserPrincipal principal) {
        return orderCourses.findByDiningOrder_IdAndCourseId(dining.getId(), course.getId()).orElseGet(() -> {
            DiningOrderCourseEntity created = new DiningOrderCourseEntity();
            created.setDiningOrder(dining);
            created.setCourseId(course.getId());
            created.setCourseName(course.getName());
            created.setSequence(course.getDisplayOrder());
            created.setStatus(CourseStatus.PENDING);
            DiningOrderCourseEntity saved = orderCourses.save(created);
            history.save(CourseStatusHistoryEntity.of(saved.getId(), null, CourseStatus.PENDING,
                    principal == null ? null : principal.userId(), roleName(principal), "Ordered"));
            return saved;
        });
    }

    // =================================================================================================================
    // Request next course (guest)
    // =================================================================================================================

    @Transactional
    public NextCourseResponse requestNextCourse(String tableToken, String sessionRef) {
        RestaurantTableEntity table = lockTable(tableByToken(tableToken).getId());
        Optional<DiningOrderEntity> active = active(table);
        if (active.isEmpty()) {
            return new NextCourseResponse(NextCourseOutcome.NO_PENDING_COURSE,
                    "You have no pending course. Please order your next course first.", null, null);
        }
        DiningOrderEntity dining = active.get();
        Optional<CourseReleaseRequestEntity> waiting = requests.findByDiningOrderIdAndStatus(dining.getId(),
                CourseReleaseRequestEntity.Status.PENDING).stream().findFirst();
        if (waiting.isPresent()) {
            String name = orderCourses.findById(waiting.get().getDiningOrderCourseId()).map(DiningOrderCourseEntity::getCourseName).orElse(null);
            return new NextCourseResponse(NextCourseOutcome.ALREADY_PENDING, "Your request is already being processed.",
                    waiting.get().getId(), name);
        }
        List<DiningOrderCourseEntity> courses = orderCourses.findByDiningOrder_IdOrderBySequenceAscCreatedAtAsc(dining.getId());
        Optional<DiningOrderCourseEntity> next = nextEligible(courses);
        if (next.isEmpty()) {
            boolean onHold = courses.stream().anyMatch(c -> c.getStatus() == CourseStatus.ON_HOLD);
            return onHold
                    ? new NextCourseResponse(NextCourseOutcome.ON_HOLD,
                            "Your next course is being held by our team. Please ask your waiter.", null, null)
                    : new NextCourseResponse(NextCourseOutcome.NO_PENDING_COURSE,
                            "You have no pending course. Please order your next course first.", null, null);
        }
        DiningOrderCourseEntity course = next.get();
        CourseReleaseRequestEntity request = new CourseReleaseRequestEntity();
        request.setDiningOrderId(dining.getId());
        request.setDiningOrderCourseId(course.getId());
        request.setBranchId(dining.getBranchId());
        request.setTableId(table.getId());
        request.setSessionRef(sessionRef == null || sessionRef.isBlank() ? null : sessionRef.trim());
        CourseReleaseRequestEntity saved = requests.save(request);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("requestId", saved.getId());
        details.put("diningOrderId", dining.getId());
        details.put("courseId", course.getId());
        details.put("courseName", course.getCourseName());
        details.put("tableNumber", table.getNumber());
        notifier.staff("COURSE_REQUESTED", dining.getBranchId(), details);
        notifier.guest("COURSE_REQUESTED", table.getQrToken(), Map.of("courseName", course.getCourseName()));
        return new NextCourseResponse(NextCourseOutcome.REQUESTED, "Your request has been sent to the waiter and cashier.",
                saved.getId(), course.getCourseName());
    }

    /** The first course (in serving order) that is ordered and waiting, not on hold. */
    private static Optional<DiningOrderCourseEntity> nextEligible(List<DiningOrderCourseEntity> courses) {
        return courses.stream()
                .filter(c -> c.getStatus() == CourseStatus.PENDING)
                .min(Comparator.comparingInt(DiningOrderCourseEntity::getSequence)
                        .thenComparing(DiningOrderCourseEntity::getCreatedAt));
    }

    // =================================================================================================================
    // Release, hold, serve, cancel (staff)
    // =================================================================================================================

    @Transactional
    public ReleaseResult releaseCourse(UUID orderCourseId, UserPrincipal principal) {
        DiningOrderCourseEntity course = orderCourses.lockById(orderCourseId)
                .orElseThrow(() -> new ApiException(404, "Course not found"));
        DiningOrderEntity dining = course.getDiningOrder();
        assertBranch(dining.getBranchId(), principal);
        if (course.isReleased()) {
            throw new ApiException(409, alreadyReleasedMessage(course));
        }
        if (dining.getStatus() != DiningOrderEntity.Status.OPEN) {
            throw new ApiException(409, "This meal is closed");
        }
        CourseStatus before = course.getStatus();
        String tableToken = dining.getTable().getQrToken();
        String tableNumber = dining.getTable().getNumber();
        List<UUID> heldIds = orderRepository.findByDiningOrderCourseIdOrderByCreatedAtAsc(orderCourseId).stream()
                .filter(order -> order.getStatus() == OrderStatus.HELD).map(OrderEntity::getId).toList();

        Instant now = Instant.now();
        // Conditional update: only HELD orders move, so no dish can reach the kitchen twice. Clears the session.
        int released = orderRepository.releaseHeldCourse(orderCourseId, principal.userId(), now);
        if (released == 0) {
            throw new ApiException(409, "There is nothing in this course to send to the kitchen");
        }

        course = orderCourses.findById(orderCourseId).orElseThrow();
        UserEntity staff = userRepository.findById(principal.userId()).orElse(null);
        course.setReleasedAt(now);
        course.setReleasedBy(principal.userId());
        course.setReleasedByName(staff == null ? null : staff.getName());
        course.setReleasedByRole(roleName(principal));
        course.setStatus(CourseStatus.RELEASED);
        orderCourses.save(course);

        List<CourseReleaseRequestEntity> pending = requests.findByDiningOrderCourseIdAndStatus(orderCourseId,
                CourseReleaseRequestEntity.Status.PENDING);
        for (CourseReleaseRequestEntity request : pending) {
            request.setStatus(CourseReleaseRequestEntity.Status.APPROVED);
            request.setHandledBy(principal.userId());
            request.setHandledByRole(roleName(principal));
            request.setHandledAt(now);
        }
        requests.saveAll(pending);
        history.save(CourseStatusHistoryEntity.of(orderCourseId, before, CourseStatus.RELEASED, principal.userId(),
                roleName(principal), pending.isEmpty() ? "Released to the kitchen" : "Released on the guest's request"));
        auditService.log(staff, AuditAction.COURSE_RELEASED, "DINING_ORDER_COURSE", orderCourseId,
                DiningCourseService.json(objectMapper, Map.of("course", course.getCourseName(), "table", tableNumber,
                        "orders", released)), null);

        // The kitchen screens and the guest's tracking follow orders: tell them each batch is now new for the kitchen.
        for (UUID orderId : heldIds) {
            orderRealtime.publishOrderStatusChanged(orderId, OrderStatus.HELD, OrderStatus.PENDING, tableToken);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("diningOrderId", dining.getId());
        details.put("courseId", orderCourseId);
        details.put("courseName", course.getCourseName());
        details.put("tableNumber", tableNumber);
        details.put("handledByName", course.getReleasedByName());
        details.put("handledByRole", course.getReleasedByRole());
        details.put("requestIds", pending.stream().map(r -> r.getId().toString()).toList());
        notifier.staff("COURSE_RELEASED", dining.getBranchId(), details);
        notifier.guest("COURSE_RELEASED", tableToken, Map.of("courseName", course.getCourseName()));

        return new ReleaseResult(courseView(course, batchesFor(List.of(orderCourseId)), Map.of()), released,
                course.getCourseName() + " for table " + tableNumber + " has been sent to the kitchen");
    }

    /** Releases the course a guest asked for. If a colleague already did, says so instead of sending it again. */
    @Transactional
    public ReleaseResult releaseRequest(UUID requestId, UserPrincipal principal) {
        CourseReleaseRequestEntity request = requests.findById(requestId)
                .orElseThrow(() -> new ApiException(404, "Request not found"));
        assertBranch(request.getBranchId(), principal);
        if (request.getStatus() == CourseReleaseRequestEntity.Status.APPROVED) {
            DiningOrderCourseEntity course = orderCourses.findById(request.getDiningOrderCourseId()).orElse(null);
            throw new ApiException(409, course == null ? "This course has already been released" : alreadyReleasedMessage(course));
        }
        if (request.getStatus() != CourseReleaseRequestEntity.Status.PENDING) {
            throw new ApiException(409, "This request is no longer waiting (" + request.getStatus().name().toLowerCase(Locale.ROOT) + ")");
        }
        return releaseCourse(request.getDiningOrderCourseId(), principal);
    }

    /** Staff close a guest's request without releasing (e.g. they spoke to the table). */
    @Transactional
    public RequestView dismissRequest(UUID requestId, UserPrincipal principal) {
        CourseReleaseRequestEntity request = requests.findById(requestId)
                .orElseThrow(() -> new ApiException(404, "Request not found"));
        assertBranch(request.getBranchId(), principal);
        if (request.getStatus() != CourseReleaseRequestEntity.Status.PENDING) {
            throw new ApiException(409, request.getStatus() == CourseReleaseRequestEntity.Status.APPROVED
                    ? "This course has already been released" : "This request is no longer waiting");
        }
        request.setStatus(CourseReleaseRequestEntity.Status.CANCELLED);
        request.setHandledBy(principal.userId());
        request.setHandledByRole(roleName(principal));
        request.setHandledAt(Instant.now());
        requests.save(request);
        RestaurantTableEntity table = tableRepository.findById(request.getTableId()).orElse(null);
        notifier.staff("COURSE_REQUEST_CANCELLED", request.getBranchId(), Map.of("requestId", request.getId()));
        notifier.guest("COURSE_REQUEST_CANCELLED", table == null ? null : table.getQrToken(), Map.of());
        return requestView(request);
    }

    /** Keeps a waiting course from being requested or released until it is resumed (e.g. the guests stepped out). */
    @Transactional
    public OrderCourseView holdCourse(UUID orderCourseId, UserPrincipal principal) {
        DiningOrderCourseEntity course = lockedForChange(orderCourseId, principal);
        if (course.getStatus() != CourseStatus.ON_HOLD) {
            changeStatus(course, CourseStatus.ON_HOLD, principal, "Put on hold");
            closeRequests(course, CourseReleaseRequestEntity.Status.CANCELLED, principal);
            audit(principal, AuditAction.COURSE_HELD, course, "held");
            notifyCourseChanged(course, "COURSE_ON_HOLD");
        }
        return courseView(course, batchesFor(List.of(orderCourseId)), Map.of());
    }

    @Transactional
    public OrderCourseView resumeCourse(UUID orderCourseId, UserPrincipal principal) {
        DiningOrderCourseEntity course = lockedForChange(orderCourseId, principal);
        if (course.getStatus() == CourseStatus.ON_HOLD) {
            changeStatus(course, CourseStatus.PENDING, principal, "Hold lifted");
            audit(principal, AuditAction.COURSE_HELD, course, "resumed");
            notifyCourseChanged(course, "COURSE_RESUMED");
        }
        return courseView(course, batchesFor(List.of(orderCourseId)), Map.of());
    }

    /** Cancels a course that has not reached the kitchen (its held orders are cancelled and drop off the bill). */
    @Transactional
    public OrderCourseView cancelCourse(UUID orderCourseId, String reason, UserPrincipal principal) {
        lockedForChange(orderCourseId, principal); // takes the row lock and refuses a course already with the kitchen
        String why = reason == null || reason.isBlank() ? "Course cancelled" : reason.trim();
        for (OrderEntity order : orderRepository.findByDiningOrderCourseIdOrderByCreatedAtAsc(orderCourseId)) {
            if (order.getStatus() == OrderStatus.HELD) {
                orderService.cancelOrder(order.getId(), principal, new OrderCancelRequest(why, null));
            }
        }
        DiningOrderCourseEntity fresh = orderCourses.findById(orderCourseId).orElseThrow();
        recompute(fresh, principal, why);
        closeRequests(fresh, CourseReleaseRequestEntity.Status.CANCELLED, principal);
        audit(principal, AuditAction.COURSE_CANCELLED, fresh, why);
        notifyCourseChanged(fresh, "COURSE_CANCELLED");
        return courseView(fresh, batchesFor(List.of(orderCourseId)), Map.of());
    }

    /** The waiter has taken the course to the table: every ready batch of it is marked served. */
    @Transactional
    public OrderCourseView serveCourse(UUID orderCourseId, UserPrincipal principal) {
        DiningOrderCourseEntity course = orderCourses.lockById(orderCourseId)
                .orElseThrow(() -> new ApiException(404, "Course not found"));
        assertBranch(course.getDiningOrder().getBranchId(), principal);
        if (!course.isReleased()) {
            throw new ApiException(409, "This course has not been sent to the kitchen yet");
        }
        List<OrderEntity> ready = orderRepository.findByDiningOrderCourseIdOrderByCreatedAtAsc(orderCourseId).stream()
                .filter(order -> order.getStatus() == OrderStatus.READY).toList();
        if (ready.isEmpty()) {
            throw new ApiException(409, course.getStatus() == CourseStatus.SERVED
                    ? "This course has already been served" : "Nothing in this course is ready to serve yet");
        }
        for (OrderEntity order : ready) {
            orderService.updateStatus(order.getId(), new OrderStatusUpdateRequest(OrderStatus.COMPLETED), principal);
        }
        DiningOrderCourseEntity fresh = orderCourses.findById(orderCourseId).orElseThrow();
        recompute(fresh, principal, "Served at the table");
        return courseView(fresh, batchesFor(List.of(orderCourseId)), Map.of());
    }

    private DiningOrderCourseEntity lockedForChange(UUID orderCourseId, UserPrincipal principal) {
        DiningOrderCourseEntity course = orderCourses.lockById(orderCourseId)
                .orElseThrow(() -> new ApiException(404, "Course not found"));
        assertBranch(course.getDiningOrder().getBranchId(), principal);
        if (course.isReleased()) {
            throw new ApiException(409, "This course is already with the kitchen");
        }
        return course;
    }

    // =================================================================================================================
    // Following the kitchen and the table
    // =================================================================================================================

    /** A course's orders moved in the kitchen (accepted, cooking, ready, served, cancelled): update the course. */
    @EventListener
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        OrderEntity order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null || order.getDiningOrderCourseId() == null) {
            return;
        }
        orderCourses.findById(order.getDiningOrderCourseId()).ifPresent(course -> {
            CourseStatus before = course.getStatus();
            if (recompute(course, null, "Kitchen: " + event.current().name().toLowerCase(Locale.ROOT))) {
                notifyCourseChanged(course, course.getStatus() == CourseStatus.READY && before != CourseStatus.READY
                        ? "COURSE_READY" : "COURSE_STATUS_CHANGED");
            }
        });
    }

    /** The party has gone: their meal closes and any course request still waiting expires. */
    @EventListener
    public void onSittingEnded(TableSittingEndedEvent event) {
        for (DiningOrderEntity dining : diningOrders.findByTable_IdAndStatus(event.tableId(), DiningOrderEntity.Status.OPEN)) {
            close(dining);
        }
    }

    private void close(DiningOrderEntity dining) {
        dining.setStatus(DiningOrderEntity.Status.CLOSED);
        dining.setClosedAt(Instant.now());
        diningOrders.save(dining);
        List<CourseReleaseRequestEntity> waiting = requests.findByDiningOrderIdAndStatus(dining.getId(),
                CourseReleaseRequestEntity.Status.PENDING);
        for (CourseReleaseRequestEntity request : waiting) {
            request.setStatus(CourseReleaseRequestEntity.Status.EXPIRED);
            request.setHandledAt(Instant.now());
        }
        requests.saveAll(waiting);
        notifier.staff("DINING_ORDER_CLOSED", dining.getBranchId(), Map.of("diningOrderId", dining.getId()));
    }

    /**
     * Works out a course's status from its orders. Returns true when it changed (and records the change).
     * Held: PENDING (or ON_HOLD). Released: RELEASED, then PREPARING, READY, PARTIALLY_SERVED and SERVED as the kitchen
     * and the waiter work through its batches. All batches cancelled: CANCELLED.
     */
    boolean recompute(DiningOrderCourseEntity course, UserPrincipal by, String reason) {
        CourseStatus next = derive(course, orderRepository.findByDiningOrderCourseIdOrderByCreatedAtAsc(course.getId()));
        if (next == course.getStatus()) {
            return false;
        }
        changeStatus(course, next, by, reason);
        return true;
    }

    static CourseStatus derive(DiningOrderCourseEntity course, List<OrderEntity> orders) {
        List<OrderEntity> live = orders.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED).toList();
        if (live.isEmpty()) {
            return orders.isEmpty() ? course.getStatus() : CourseStatus.CANCELLED;
        }
        if (!course.isReleased()) {
            return course.getStatus() == CourseStatus.ON_HOLD ? CourseStatus.ON_HOLD : CourseStatus.PENDING;
        }
        long served = live.stream().filter(o -> o.getStatus() == OrderStatus.COMPLETED).count();
        if (served == live.size()) {
            return CourseStatus.SERVED;
        }
        if (served > 0) {
            return CourseStatus.PARTIALLY_SERVED;
        }
        if (live.stream().allMatch(o -> o.getStatus() == OrderStatus.READY)) {
            return CourseStatus.READY;
        }
        if (live.stream().anyMatch(o -> o.getStatus() == OrderStatus.PREPARING || o.getStatus() == OrderStatus.READY)) {
            return CourseStatus.PREPARING;
        }
        return CourseStatus.RELEASED;
    }

    private void changeStatus(DiningOrderCourseEntity course, CourseStatus next, UserPrincipal by, String reason) {
        CourseStatus before = course.getStatus();
        course.setStatus(next);
        if (next == CourseStatus.SERVED && course.getServedAt() == null) {
            course.setServedAt(Instant.now());
        }
        orderCourses.save(course);
        history.save(CourseStatusHistoryEntity.of(course.getId(), before, next, by == null ? null : by.userId(),
                roleName(by), reason));
    }

    private void closeRequests(DiningOrderCourseEntity course, CourseReleaseRequestEntity.Status status, UserPrincipal by) {
        List<CourseReleaseRequestEntity> waiting = requests.findByDiningOrderCourseIdAndStatus(course.getId(),
                CourseReleaseRequestEntity.Status.PENDING);
        for (CourseReleaseRequestEntity request : waiting) {
            request.setStatus(status);
            request.setHandledBy(by == null ? null : by.userId());
            request.setHandledByRole(roleName(by));
            request.setHandledAt(Instant.now());
        }
        requests.saveAll(waiting);
    }

    private void notifyCourseChanged(DiningOrderCourseEntity course, String event) {
        DiningOrderEntity dining = course.getDiningOrder();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("diningOrderId", dining.getId());
        details.put("courseId", course.getId());
        details.put("courseName", course.getCourseName());
        details.put("status", course.getStatus());
        details.put("tableNumber", dining.getTable().getNumber());
        notifier.staff(event, dining.getBranchId(), details);
        notifier.guest(event, dining.getTable().getQrToken(), Map.of("courseName", course.getCourseName(), "status", course.getStatus()));
    }

    // =================================================================================================================
    // Reading
    // =================================================================================================================

    @Transactional(readOnly = true)
    public DiningOrderView get(UUID diningOrderId, UserPrincipal principal) {
        DiningOrderEntity dining = diningOrders.findById(diningOrderId).orElseThrow(() -> new ApiException(404, "Meal not found"));
        assertBranch(dining.getBranchId(), principal);
        return view(dining);
    }

    /** The table's open meal, or 404 when nothing is open. */
    @Transactional(readOnly = true)
    public DiningOrderView activeForTable(UUID tableId, UserPrincipal principal) {
        RestaurantTableEntity table = tableRepository.findById(tableId).orElseThrow(() -> new ApiException(404, "Table not found"));
        assertBranch(table.getBranch() == null ? null : table.getBranch().getId(), principal);
        return active(table).map(this::view).orElseThrow(() -> new ApiException(404, "This table has no meal in progress"));
    }

    /** Open meals for the staff dashboard (the caller's branch, or every branch for unscoped staff). */
    @Transactional(readOnly = true)
    public List<DiningOrderView> openMeals(UserPrincipal principal) {
        return diningOrders.findForStaff(DiningOrderEntity.Status.OPEN, DiningCourseService.scopedBranch(principal)).stream()
                .filter(dining -> active(dining.getTable()).map(a -> a.getId().equals(dining.getId())).orElse(false))
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RequestView> listRequests(boolean pendingOnly, UserPrincipal principal) {
        Collection<CourseReleaseRequestEntity.Status> statuses = pendingOnly
                ? EnumSet.of(CourseReleaseRequestEntity.Status.PENDING)
                : EnumSet.allOf(CourseReleaseRequestEntity.Status.class);
        return requests.findForStaff(statuses, DiningCourseService.scopedBranch(principal), Instant.now().minus(REQUEST_WINDOW))
                .stream().map(this::requestView).toList();
    }

    @Transactional(readOnly = true)
    public PublicDiningView publicView(String tableToken) {
        return publicView(tableByToken(tableToken));
    }

    private PublicDiningView publicView(RestaurantTableEntity table) {
        UUID branchId = table.getBranch() == null ? null : table.getBranch().getId();
        List<DiningDtos.CourseView> offered = courseService.offeredAt(branchId).stream().map(DiningCourseService::toView).toList();
        Optional<DiningOrderEntity> active = active(table);
        if (active.isEmpty()) {
            return new PublicDiningView(table.getNumber(), offered, null, List.of(), false, null, null);
        }
        DiningOrderEntity dining = active.get();
        List<DiningOrderCourseEntity> courses = orderCourses.findByDiningOrder_IdOrderBySequenceAscCreatedAtAsc(dining.getId());
        Map<UUID, List<BatchView>> batches = batchesFor(courses.stream().map(DiningOrderCourseEntity::getId).toList());
        List<PublicCourseView> mealCourses = courses.stream().map(course -> new PublicCourseView(course.getId(), course.getCourseId(),
                course.getCourseName(), course.getSequence(), course.getStatus(), course.getReleasedAt(),
                batches.getOrDefault(course.getId(), List.of()).stream()
                        .filter(batch -> batch.status() != OrderStatus.CANCELLED)
                        .flatMap(batch -> batch.items().stream())
                        .map(item -> new PublicItemView(item.name(), item.quantity(), item.modifiers(), item.notes()))
                        .toList())).toList();
        CourseReleaseRequestEntity waiting = requests.findByDiningOrderIdAndStatus(dining.getId(),
                CourseReleaseRequestEntity.Status.PENDING).stream().findFirst().orElse(null);
        Optional<DiningOrderCourseEntity> next = nextEligible(courses);
        PublicRequestView pending = waiting == null ? null : new PublicRequestView(waiting.getId(),
                courses.stream().filter(c -> c.getId().equals(waiting.getDiningOrderCourseId())).map(DiningOrderCourseEntity::getCourseName)
                        .findFirst().orElse(null), waiting.getStatus(), waiting.getRequestedAt());
        return new PublicDiningView(table.getNumber(), offered, dining.getId(), mealCourses,
                waiting == null && next.isPresent(), next.map(DiningOrderCourseEntity::getCourseName).orElse(null), pending);
    }

    private DiningOrderView view(DiningOrderEntity dining) {
        RestaurantTableEntity table = dining.getTable();
        List<DiningOrderCourseEntity> courses = orderCourses.findByDiningOrder_IdOrderBySequenceAscCreatedAtAsc(dining.getId());
        Map<UUID, List<BatchView>> byCourse = batchesFor(courses.stream().map(DiningOrderCourseEntity::getId).toList());
        List<CourseReleaseRequestEntity> waiting = requests.findByDiningOrderIdAndStatus(dining.getId(), CourseReleaseRequestEntity.Status.PENDING);
        Map<UUID, UUID> requestByCourse = new HashMap<>();
        waiting.forEach(r -> requestByCourse.put(r.getDiningOrderCourseId(), r.getId()));

        // Everything of the sitting sent without a course: this meal's own, plus orders placed the ordinary way (POS,
        // older apps) while the meal is open, so the waiter sees the whole table in one place.
        List<OrderEntity> sitting = dining.getStatus() == DiningOrderEntity.Status.OPEN
                ? tableSitting.visibleToGuests(table, orderRepository.findByTableIdOrderByCreatedAtAsc(table.getId()))
                : orderRepository.findByDiningOrderIdOrderByCreatedAtAsc(dining.getId());
        List<OrderEntity> immediate = sitting.stream()
                .filter(o -> o.getDiningOrderCourseId() == null)
                .filter(o -> o.getDiningOrderId() == null || o.getDiningOrderId().equals(dining.getId()))
                .toList();

        List<OrderCourseView> courseViews = courses.stream()
                .map(course -> courseView(course, byCourse, requestByCourse)).toList();
        Optional<DiningOrderCourseEntity> next = nextEligible(courses);
        return new DiningOrderView(dining.getId(), number(dining.getId()), table.getId(), table.getNumber(), dining.getBranchId(),
                dining.getStatus(), dining.getOpenedAt(), courseViews, batches(immediate),
                next.map(DiningOrderCourseEntity::getId).orElse(null),
                waiting.isEmpty() ? null : requestView(waiting.get(0)));
    }

    private OrderCourseView courseView(DiningOrderCourseEntity course, Map<UUID, List<BatchView>> byCourse, Map<UUID, UUID> requestByCourse) {
        List<BatchView> batches = byCourse.getOrDefault(course.getId(), List.of());
        int itemCount = batches.stream().filter(b -> b.status() != OrderStatus.CANCELLED)
                .flatMap(b -> b.items().stream()).mapToInt(ItemView::quantity).sum();
        return new OrderCourseView(course.getId(), course.getCourseId(), course.getCourseName(), course.getSequence(),
                course.getStatus(), course.getReleasedAt(), course.getReleasedByName(), course.getReleasedByRole(),
                course.getServedAt(), itemCount, requestByCourse.get(course.getId()), batches);
    }

    private Map<UUID, List<BatchView>> batchesFor(Collection<UUID> orderCourseIds) {
        Map<UUID, List<BatchView>> result = new HashMap<>();
        for (UUID id : orderCourseIds) {
            result.put(id, batches(orderRepository.findByDiningOrderCourseIdOrderByCreatedAtAsc(id)));
        }
        return result;
    }

    private List<BatchView> batches(List<OrderEntity> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<OrderItemEntity> items = orderItemRepository.findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList());
        Map<UUID, List<String>> modifiers = new HashMap<>();
        if (!items.isEmpty()) {
            for (OrderItemModifierEntity modifier : modifierRepository.findByOrderItem_IdInOrderByCreatedAtAsc(
                    items.stream().map(OrderItemEntity::getId).toList())) {
                modifiers.computeIfAbsent(modifier.getOrderItem().getId(), k -> new ArrayList<>()).add(modifier.getOptionNameSnapshot());
            }
        }
        Map<UUID, List<ItemView>> itemsByOrder = new HashMap<>();
        for (OrderItemEntity item : items) {
            itemsByOrder.computeIfAbsent(item.getOrder().getId(), k -> new ArrayList<>()).add(new ItemView(item.getId(),
                    item.getNameSnapshot(), item.getQuantity(), item.getPriceSnapshot(), item.getNotes(),
                    modifiers.getOrDefault(item.getId(), List.of()), item.getReadyAt() != null));
        }
        return orders.stream().map(order -> new BatchView(order.getId(), number(order.getId()), order.getStatus(),
                order.getKitchenStation(), order.getCreatedAt(), order.getReleasedAt(),
                order.getTotal() == null ? BigDecimal.ZERO : order.getTotal(),
                itemsByOrder.getOrDefault(order.getId(), List.of()))).toList();
    }

    private RequestView requestView(CourseReleaseRequestEntity request) {
        DiningOrderCourseEntity course = orderCourses.findById(request.getDiningOrderCourseId()).orElse(null);
        RestaurantTableEntity table = tableRepository.findById(request.getTableId()).orElse(null);
        String handledByName = request.getHandledBy() == null ? null
                : userRepository.findById(request.getHandledBy()).map(UserEntity::getName).orElse(null);
        List<ItemView> items = course == null ? List.of() : batchesFor(List.of(course.getId())).getOrDefault(course.getId(), List.of())
                .stream().filter(b -> b.status() != OrderStatus.CANCELLED).flatMap(b -> b.items().stream()).toList();
        return new RequestView(request.getId(), request.getDiningOrderId(), number(request.getDiningOrderId()),
                request.getDiningOrderCourseId(), course == null ? null : course.getCourseName(),
                course == null ? null : course.getStatus(), request.getTableId(), table == null ? null : table.getNumber(),
                request.getBranchId(), request.getStatus(), request.getRequestedAt(), request.getHandledBy(), handledByName,
                request.getHandledByRole(), request.getHandledAt(), items);
    }

    // =================================================================================================================
    // Meal lookup
    // =================================================================================================================

    /** The table's meal in progress, opening one if there is none (the caller holds the table lock). */
    private DiningOrderEntity activeOrOpen(RestaurantTableEntity table) {
        Optional<DiningOrderEntity> active = active(table);
        if (active.isPresent()) {
            return active.get();
        }
        // A meal left open by a party that has gone (e.g. its last order was settled outside this module): close it.
        diningOrders.findByTable_IdAndStatus(table.getId(), DiningOrderEntity.Status.OPEN).forEach(this::close);
        diningOrders.flush();
        DiningOrderEntity dining = new DiningOrderEntity();
        dining.setTable(table);
        dining.setBranchId(table.getBranch() == null ? null : table.getBranch().getId());
        return diningOrders.saveAndFlush(dining);
    }

    /**
     * The table's open meal, if it still belongs to the current sitting: at least one of its orders is among what the
     * party at the table can see (unpaid, or still with the kitchen).
     */
    private Optional<DiningOrderEntity> active(RestaurantTableEntity table) {
        Optional<DiningOrderEntity> open = diningOrders.findFirstByTable_IdAndStatusOrderByOpenedAtDesc(table.getId(),
                DiningOrderEntity.Status.OPEN);
        if (open.isEmpty()) {
            return Optional.empty();
        }
        Set<UUID> current = new HashSet<>();
        tableSitting.visibleToGuests(table, orderRepository.findByTableIdOrderByCreatedAtAsc(table.getId()))
                .forEach(order -> current.add(order.getId()));
        boolean inSitting = orderRepository.findByDiningOrderIdOrderByCreatedAtAsc(open.get().getId()).stream()
                .anyMatch(order -> current.contains(order.getId()));
        return inSitting ? open : Optional.empty();
    }

    private RestaurantTableEntity tableByToken(String tableToken) {
        if (tableToken == null || tableToken.isBlank()) {
            throw new ApiException(404, "Table not found");
        }
        return tableRepository.findByQrToken(tableToken.trim()).orElseThrow(() -> new ApiException(404, "Table not found"));
    }

    private RestaurantTableEntity lockTable(UUID tableId) {
        return diningOrders.lockTable(tableId).orElseThrow(() -> new ApiException(404, "Table not found"));
    }

    // =================================================================================================================
    // Helpers
    // =================================================================================================================

    /** Branch staff only act on their own branch's tables; meals with no branch are shared, as for orders. */
    private static void assertBranch(UUID branchId, UserPrincipal principal) {
        UUID scoped = DiningCourseService.scopedBranch(principal);
        if (scoped != null && branchId != null && !scoped.equals(branchId)) {
            throw new ApiException(403, "Forbidden");
        }
    }

    private static String alreadyReleasedMessage(DiningOrderCourseEntity course) {
        String who = course.getReleasedByName() == null ? "a colleague" : course.getReleasedByName();
        return course.getCourseName() + " has already been released to the kitchen by " + who;
    }

    private void audit(UserPrincipal principal, AuditAction action, DiningOrderCourseEntity course, String what) {
        auditService.log(userRepository.findById(principal.userId()).orElse(null), action, "DINING_ORDER_COURSE", course.getId(),
                DiningCourseService.json(objectMapper, Map.of("course", course.getCourseName(), "change", what)), null);
    }

    private static String roleName(UserPrincipal principal) {
        return principal == null || principal.role() == null ? null : principal.role().name();
    }

    static String number(UUID id) {
        return id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
