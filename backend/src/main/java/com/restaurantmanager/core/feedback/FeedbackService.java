package com.restaurantmanager.core.feedback;

import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.notification.whatsapp.WhatsAppService;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderItemEntity;
import com.restaurantmanager.core.order.OrderItemRepository;
import com.restaurantmanager.core.order.OrderRepository;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.order.OrderType;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Order ratings. Each rating belongs to the order's branch (branch managers see and answer their own branch's), and
 * is attributed to the rider who delivered it and the staff member who took it. Signed-in customers can rate for
 * {@code app.feedback.customer-window-days} after the order finished; table guests for two hours, and may leave a
 * phone number to receive the reply.
 */
@Service
public class FeedbackService {
    private static final Set<OrderStatus> RATEABLE = Set.of(OrderStatus.COMPLETED, OrderStatus.DELIVERED);
    private static final int LOW_RATING = 2;
    /** Guests at a table (no account) can rate an order for this long after it was served. */
    static final Duration GUEST_RATING_WINDOW = Duration.ofHours(2);

    private final OrderFeedbackRepository feedbackRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final WhatsAppService whatsAppService;
    private final Duration customerWindow;
    private final ZoneId zone;

    public FeedbackService(OrderFeedbackRepository feedbackRepository,
                           OrderRepository orderRepository,
                           OrderItemRepository orderItemRepository,
                           UserRepository userRepository,
                           BranchRepository branchRepository,
                           SimpMessagingTemplate messagingTemplate,
                           ApplicationEventPublisher eventPublisher,
                           WhatsAppService whatsAppService,
                           @Value("${app.feedback.customer-window-days:14}") long customerWindowDays,
                           @Value("${app.timezone:Africa/Accra}") String timeZone) {
        this.feedbackRepository = feedbackRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.messagingTemplate = messagingTemplate;
        this.eventPublisher = eventPublisher;
        this.whatsAppService = whatsAppService;
        this.customerWindow = Duration.ofDays(customerWindowDays);
        this.zone = ZoneId.of(timeZone);
    }

    @Transactional
    public FeedbackView submitAsCustomer(UUID orderId, FeedbackRequest request, UserPrincipal principal) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow(() -> new ApiException(404, "Order not found"));
        if (order.getCustomerUserId() == null || !order.getCustomerUserId().equals(principal.userId())) {
            throw new ApiException(403, "You can only rate your own orders");
        }
        Instant finished = finishedAt(order);
        if (RATEABLE.contains(order.getStatus()) && finished != null && finished.plus(customerWindow).isBefore(Instant.now())) {
            throw new ApiException(410, "This order can no longer be rated (ratings are open for "
                    + customerWindow.toDays() + " days after the order)");
        }
        return save(order, request, principal.userId(), null, null);
    }

    /** Guests who ordered from a table QR can rate their order without an account. */
    @Transactional
    public FeedbackView submitAsGuest(String tableToken, UUID orderId, FeedbackRequest request) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow(() -> new ApiException(404, "Order not found"));
        if (order.getTable() == null || !tableToken.equals(order.getTable().getQrToken())) {
            throw new ApiException(404, "Order not found for this table");
        }
        if (guestWindowClosed(order)) {
            throw new ApiException(410, "This order can no longer be rated from the table");
        }
        return save(order, request, null, request.guestName(), request.guestPhone());
    }

    /**
     * Whether the phone that placed a table order may still offer to rate it, and the manager's reply once there is
     * one. 404 for an order from another table; 410 once it was cancelled or served more than two hours ago.
     */
    @Transactional(readOnly = true)
    public GuestRatingPrompt guestRatingPrompt(String tableToken, UUID orderId) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow(() -> new ApiException(404, "Order not found"));
        if (order.getTable() == null || order.getType() != OrderType.DINE_IN || !tableToken.equals(order.getTable().getQrToken())) {
            throw new ApiException(404, "Order not found for this table");
        }
        if (order.getStatus() == OrderStatus.CANCELLED || guestWindowClosed(order)) {
            throw new ApiException(410, "This order can no longer be rated from the table");
        }
        boolean finished = order.getStatus() == OrderStatus.COMPLETED;
        Instant servedAt = finished ? servedAt(order) : null;
        OrderFeedbackEntity feedback = feedbackRepository.findByOrderId(order.getId()).orElse(null);
        return new GuestRatingPrompt(order.getId(), order.getId().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT),
                order.getStatus(), finished, feedback != null, servedAt,
                servedAt == null ? null : servedAt.plus(GUEST_RATING_WINDOW),
                feedback == null ? null : feedback.getResponse(), feedback == null ? null : feedback.getRespondedAt());
    }

    private static Instant servedAt(OrderEntity order) {
        return order.getServedAt() != null ? order.getServedAt() : order.getUpdatedAt();
    }

    /** When the order finished: delivered, or served/completed. */
    private static Instant finishedAt(OrderEntity order) {
        if (order.getDeliveredAt() != null) {
            return order.getDeliveredAt();
        }
        return servedAt(order);
    }

    private static boolean guestWindowClosed(OrderEntity order) {
        if (order.getStatus() != OrderStatus.COMPLETED) {
            return false;
        }
        Instant served = servedAt(order);
        return served != null && served.plus(GUEST_RATING_WINDOW).isBefore(Instant.now());
    }

    @Transactional(readOnly = true)
    public FeedbackView forOrder(UUID orderId, UserPrincipal principal) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow(() -> new ApiException(404, "Order not found"));
        if (principal.role() == Role.CUSTOMER && !principal.userId().equals(order.getCustomerUserId())) {
            throw new ApiException(403, "Forbidden");
        }
        return feedbackRepository.findByOrderId(orderId).map(f -> toView(f, names(List.of(f)), branchNames()))
                .orElseThrow(() -> new ApiException(404, "No feedback for this order"));
    }

    @Transactional(readOnly = true)
    public List<FeedbackView> list(UserPrincipal principal, LocalDate from, LocalDate to, Integer maxRating, UUID branchId) {
        List<OrderFeedbackEntity> rows = range(principal, from, to, branchId).stream()
                .filter(f -> maxRating == null || f.getRating() <= maxRating)
                .toList();
        Map<UUID, String> names = names(rows);
        Map<UUID, String> branches = branchNames();
        return rows.stream().map(f -> toView(f, names, branches)).toList();
    }

    @Transactional(readOnly = true)
    public Summary summary(UserPrincipal principal, LocalDate from, LocalDate to, UUID branchId) {
        List<OrderFeedbackEntity> rows = range(principal, from, to, branchId);
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int stars = 5; stars >= 1; stars--) {
            int s = stars;
            distribution.put(stars, rows.stream().filter(r -> r.getRating() == s).count());
        }
        long responded = rows.stream().filter(r -> r.getResponse() != null).count();
        return new Summary(rows.size(), average(rows, OrderFeedbackEntity::getRating), average(rows, OrderFeedbackEntity::getFoodRating),
                average(rows, OrderFeedbackEntity::getServiceRating), average(rows, OrderFeedbackEntity::getDeliveryRating),
                distribution, rows.stream().filter(r -> r.getRating() <= LOW_RATING).count(), responded);
    }

    /** Averages per rider (delivery), per staff member (service) and per dish (food), for the same scope. */
    @Transactional(readOnly = true)
    public Breakdown breakdown(UserPrincipal principal, LocalDate from, LocalDate to, UUID branchId) {
        List<OrderFeedbackEntity> rows = range(principal, from, to, branchId);
        Map<UUID, String> names = names(rows);

        List<PersonScore> riders = rows.stream().filter(r -> r.getRiderUserId() != null)
                .collect(Collectors.groupingBy(OrderFeedbackEntity::getRiderUserId)).entrySet().stream()
                .map(e -> new PersonScore(e.getKey(), names.get(e.getKey()), e.getValue().size(),
                        average(e.getValue(), OrderFeedbackEntity::getDeliveryRating), average(e.getValue(), OrderFeedbackEntity::getRating)))
                .sorted(Comparator.comparing(PersonScore::count).reversed())
                .toList();
        List<PersonScore> staff = rows.stream().filter(r -> r.getStaffUserId() != null)
                .collect(Collectors.groupingBy(OrderFeedbackEntity::getStaffUserId)).entrySet().stream()
                .map(e -> new PersonScore(e.getKey(), names.get(e.getKey()), e.getValue().size(),
                        average(e.getValue(), OrderFeedbackEntity::getServiceRating), average(e.getValue(), OrderFeedbackEntity::getRating)))
                .sorted(Comparator.comparing(PersonScore::count).reversed())
                .toList();

        // A dish gets the food (and overall) rating of every rated order it was in.
        Map<UUID, OrderFeedbackEntity> byOrder = rows.stream().collect(Collectors.toMap(OrderFeedbackEntity::getOrderId, f -> f, (a, b) -> a));
        Map<UUID, List<OrderFeedbackEntity>> byDish = new LinkedHashMap<>();
        Map<UUID, String> dishNames = new HashMap<>();
        if (!byOrder.isEmpty()) {
            for (OrderItemEntity item : orderItemRepository.findByOrderIdIn(byOrder.keySet())) {
                if (item.getMenuItem() == null) continue;
                UUID dish = item.getMenuItem().getId();
                dishNames.putIfAbsent(dish, item.getNameSnapshot() != null ? item.getNameSnapshot() : item.getMenuItem().getName());
                List<OrderFeedbackEntity> list = byDish.computeIfAbsent(dish, k -> new ArrayList<>());
                OrderFeedbackEntity feedback = byOrder.get(item.getOrder().getId());
                if (!list.contains(feedback)) list.add(feedback);
            }
        }
        List<DishScore> dishes = byDish.entrySet().stream()
                .map(e -> new DishScore(e.getKey(), dishNames.get(e.getKey()), e.getValue().size(),
                        average(e.getValue(), OrderFeedbackEntity::getFoodRating), average(e.getValue(), OrderFeedbackEntity::getRating)))
                .sorted(Comparator.comparing(DishScore::count).reversed())
                .toList();
        return new Breakdown(riders, staff, dishes);
    }

    @Transactional
    public FeedbackView respond(UUID feedbackId, String response, UserPrincipal principal) {
        OrderFeedbackEntity feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ApiException(404, "Feedback not found"));
        if (!canSee(feedback, principal)) {
            throw new ApiException(403, "You can only answer feedback for your own branch");
        }
        feedback.setResponse(response.trim());
        feedback.setRespondedBy(principal.userId());
        feedback.setRespondedAt(Instant.now());
        feedbackRepository.save(feedback);
        String phone = feedback.getCustomerUserId() != null
                ? userRepository.findById(feedback.getCustomerUserId()).map(UserEntity::getPhone).orElse(null)
                : feedback.getGuestPhone();
        if (phone != null && !phone.isBlank()) {
            whatsAppService.sendStatusUpdate(phone, "Thanks for your feedback. " + response.trim());
        }
        return toView(feedback, names(List.of(feedback)), branchNames());
    }

    private FeedbackView save(OrderEntity order, FeedbackRequest request, UUID customerId, String guestName, String guestPhone) {
        if (!RATEABLE.contains(order.getStatus())) {
            throw new ApiException(409, "Orders can be rated once they are completed or delivered");
        }
        if (feedbackRepository.existsByOrderId(order.getId())) {
            throw new ApiException(409, "This order has already been rated");
        }
        if (request.deliveryRating() != null && order.getType() != OrderType.DELIVERY) {
            throw new ApiException(400, "Delivery rating only applies to delivery orders");
        }
        OrderFeedbackEntity feedback = new OrderFeedbackEntity();
        feedback.setOrderId(order.getId());
        feedback.setCustomerUserId(customerId);
        feedback.setGuestName(guestName == null || guestName.isBlank() ? null : guestName.trim());
        feedback.setGuestPhone(guestPhone == null || guestPhone.isBlank() ? null : guestPhone.replaceAll("\\s", ""));
        feedback.setBranchId(order.getBranch() == null ? null : order.getBranch().getId());
        feedback.setRiderUserId(order.getRiderUserId());
        feedback.setStaffUserId(order.getCreatedByUserId());
        feedback.setRating(request.rating());
        feedback.setFoodRating(request.foodRating());
        feedback.setServiceRating(request.serviceRating());
        feedback.setDeliveryRating(request.deliveryRating());
        feedback.setComment(request.comment() == null || request.comment().isBlank() ? null : request.comment().trim());
        feedbackRepository.save(feedback);
        FeedbackView view = toView(feedback, names(List.of(feedback)), branchNames());
        if (feedback.getRating() <= LOW_RATING) {
            // Managers' screens show it only for their branch (the view carries branchId).
            messagingTemplate.convertAndSend("/topic/feedback.alerts", view);
            eventPublisher.publishEvent(new LowRatingEvent(view));
        }
        return view;
    }

    /**
     * Ratings in a date range (the venue's days), in the caller's scope: managers who belong to a branch see their
     * branch (and ratings of orders with no branch, a shared queue); others see everything, or one branch when asked.
     */
    private List<OrderFeedbackEntity> range(UserPrincipal principal, LocalDate from, LocalDate to, UUID branchId) {
        UUID scope = branchBound(principal) ? principal.branchId() : branchId;
        if (branchBound(principal) && branchId != null && !branchId.equals(principal.branchId())) {
            throw new ApiException(403, "You can only see feedback for your own branch");
        }
        Instant start = from == null ? Instant.EPOCH : from.atStartOfDay(zone).toInstant();
        Instant end = to == null ? Instant.parse("9999-12-31T00:00:00Z") : to.plusDays(1).atStartOfDay(zone).toInstant();
        return feedbackRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(start, end).stream()
                // Branch managers also see feedback on orders that have no branch: like those orders, it is shared.
                .filter(f -> scope == null || scope.equals(f.getBranchId()) || (branchBound(principal) && f.getBranchId() == null))
                .toList();
    }

    private static boolean branchBound(UserPrincipal principal) {
        return (principal.role() == Role.MANAGER || principal.role() == Role.CASHIER) && principal.branchId() != null;
    }

    private static boolean canSee(OrderFeedbackEntity feedback, UserPrincipal principal) {
        return !branchBound(principal) || feedback.getBranchId() == null || principal.branchId().equals(feedback.getBranchId());
    }

    private static BigDecimal average(List<OrderFeedbackEntity> rows, Function<OrderFeedbackEntity, Integer> field) {
        List<Integer> values = rows.stream().map(field).filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return null;
        }
        return BigDecimal.valueOf(values.stream().mapToInt(Integer::intValue).sum())
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    /** Names of the customers, riders and staff in these ratings, in one query. */
    private Map<UUID, String> names(List<OrderFeedbackEntity> rows) {
        Set<UUID> ids = new HashSet<>();
        for (OrderFeedbackEntity f : rows) {
            if (f.getCustomerUserId() != null) ids.add(f.getCustomerUserId());
            if (f.getRiderUserId() != null) ids.add(f.getRiderUserId());
            if (f.getStaffUserId() != null) ids.add(f.getStaffUserId());
        }
        Map<UUID, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            userRepository.findAllById(ids).forEach(user -> names.put(user.getId(), user.getName()));
        }
        return names;
    }

    private Map<UUID, String> branchNames() {
        return branchRepository.findAll().stream().collect(Collectors.toMap(BranchEntity::getId, BranchEntity::getName));
    }

    private FeedbackView toView(OrderFeedbackEntity e, Map<UUID, String> names, Map<UUID, String> branches) {
        String customerName = e.getCustomerUserId() == null ? e.getGuestName() : names.get(e.getCustomerUserId());
        return new FeedbackView(e.getId(), e.getOrderId(), e.getCustomerUserId(), customerName, e.getRating(), e.getFoodRating(),
                e.getServiceRating(), e.getDeliveryRating(), e.getComment(), e.getResponse(), e.getRespondedAt(), e.getCreatedAt(),
                e.getBranchId(), e.getBranchId() == null ? null : branches.get(e.getBranchId()),
                e.getRiderUserId(), e.getRiderUserId() == null ? null : names.get(e.getRiderUserId()),
                e.getStaffUserId(), e.getStaffUserId() == null ? null : names.get(e.getStaffUserId()),
                e.getCustomerUserId() != null || e.getGuestPhone() != null);
    }

    public record GuestRatingPrompt(UUID orderId, String orderNumber, OrderStatus status, boolean rateable, boolean rated,
                                    Instant servedAt, Instant rateUntil, String response, Instant respondedAt) {}

    public record FeedbackRequest(@Min(1) @Max(5) int rating,
                                  @Min(1) @Max(5) Integer foodRating,
                                  @Min(1) @Max(5) Integer serviceRating,
                                  @Min(1) @Max(5) Integer deliveryRating,
                                  @Size(max = 2000) String comment,
                                  @Size(max = 120) String guestName,
                                  /** Optional, guests only: where to send the manager's reply. */
                                  @Pattern(regexp = "^\\+?[0-9 ]{7,20}$", message = "Enter a valid phone number") String guestPhone) {}

    public record RespondRequest(@NotBlank @Size(max = 2000) String response) {}

    /** {@code canReceiveReply}: a signed-in customer, or a guest who left a phone number. */
    public record FeedbackView(UUID id, UUID orderId, UUID customerUserId, String customerName, int rating, Integer foodRating,
                               Integer serviceRating, Integer deliveryRating, String comment, String response,
                               Instant respondedAt, Instant createdAt,
                               UUID branchId, String branchName, UUID riderUserId, String riderName,
                               UUID staffUserId, String staffName, boolean canReceiveReply) {}

    public record Summary(long count, BigDecimal averageRating, BigDecimal averageFood, BigDecimal averageService,
                          BigDecimal averageDelivery, Map<Integer, Long> distribution, long lowRatings, long responded) {}

    /** {@code average}: delivery for riders, service for staff, food for dishes. */
    public record PersonScore(UUID userId, String name, int count, BigDecimal average, BigDecimal averageOverall) {}

    public record DishScore(UUID menuItemId, String name, int count, BigDecimal averageFood, BigDecimal averageOverall) {}

    public record Breakdown(List<PersonScore> riders, List<PersonScore> staff, List<DishScore> dishes) {}

    public record LowRatingEvent(FeedbackView feedback) {}
}
