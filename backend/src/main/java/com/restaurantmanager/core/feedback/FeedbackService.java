package com.restaurantmanager.core.feedback;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.notification.whatsapp.WhatsAppService;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderRepository;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.order.OrderType;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Service
public class FeedbackService {
    private static final Set<OrderStatus> RATEABLE = Set.of(OrderStatus.COMPLETED, OrderStatus.DELIVERED);
    private static final int LOW_RATING = 2;
    /** Guests at a table (no account) can rate an order for this long after it was served. */
    static final java.time.Duration GUEST_RATING_WINDOW = java.time.Duration.ofHours(2);

    private final OrderFeedbackRepository feedbackRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final WhatsAppService whatsAppService;

    public FeedbackService(OrderFeedbackRepository feedbackRepository,
                           OrderRepository orderRepository,
                           UserRepository userRepository,
                           SimpMessagingTemplate messagingTemplate,
                           ApplicationEventPublisher eventPublisher,
                           WhatsAppService whatsAppService) {
        this.feedbackRepository = feedbackRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.eventPublisher = eventPublisher;
        this.whatsAppService = whatsAppService;
    }

    @Transactional
    public FeedbackView submitAsCustomer(UUID orderId, FeedbackRequest request, UserPrincipal principal) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow(() -> new ApiException(404, "Order not found"));
        if (order.getCustomerUserId() == null || !order.getCustomerUserId().equals(principal.userId())) {
            throw new ApiException(403, "You can only rate your own orders");
        }
        return save(order, request, principal.userId(), null);
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
        return save(order, request, null, request.guestName());
    }

    /**
     * Whether the phone that placed a table order may still offer to rate it. The phone keeps its own order numbers
     * (the table's pages never list a previous party's orders); this answers for one order it already knows.
     * 404 for an order from another table; 410 once it was cancelled or served more than two hours ago, so the phone
     * forgets it.
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
        return new GuestRatingPrompt(order.getId(), order.getId().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT),
                order.getStatus(), finished, feedbackRepository.existsByOrderId(order.getId()), servedAt,
                servedAt == null ? null : servedAt.plus(GUEST_RATING_WINDOW));
    }

    private static Instant servedAt(OrderEntity order) {
        return order.getServedAt() != null ? order.getServedAt() : order.getUpdatedAt();
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
        return feedbackRepository.findByOrderId(orderId).map(this::toView)
                .orElseThrow(() -> new ApiException(404, "No feedback for this order"));
    }

    @Transactional(readOnly = true)
    public List<FeedbackView> list(LocalDate from, LocalDate to, Integer maxRating) {
        return range(from, to).stream()
                .filter(f -> maxRating == null || f.getRating() <= maxRating)
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Summary summary(LocalDate from, LocalDate to) {
        List<OrderFeedbackEntity> rows = range(from, to);
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int stars = 5; stars >= 1; stars--) {
            int s = stars;
            distribution.put(stars, rows.stream().filter(r -> r.getRating() == s).count());
        }
        long responded = rows.stream().filter(r -> r.getResponse() != null).count();
        return new Summary(rows.size(), average(rows, r -> r.getRating()), average(rows, OrderFeedbackEntity::getFoodRating),
                average(rows, OrderFeedbackEntity::getServiceRating), average(rows, OrderFeedbackEntity::getDeliveryRating),
                distribution, rows.stream().filter(r -> r.getRating() <= LOW_RATING).count(), responded);
    }

    @Transactional
    public FeedbackView respond(UUID feedbackId, String response, UUID managerId) {
        OrderFeedbackEntity feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ApiException(404, "Feedback not found"));
        feedback.setResponse(response.trim());
        feedback.setRespondedBy(managerId);
        feedback.setRespondedAt(Instant.now());
        feedbackRepository.save(feedback);
        if (feedback.getCustomerUserId() != null) {
            userRepository.findById(feedback.getCustomerUserId())
                    .map(UserEntity::getPhone)
                    .filter(phone -> phone != null && !phone.isBlank())
                    .ifPresent(phone -> whatsAppService.sendStatusUpdate(phone, "Thanks for your feedback. " + response.trim()));
        }
        return toView(feedback);
    }

    private FeedbackView save(OrderEntity order, FeedbackRequest request, UUID customerId, String guestName) {
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
        feedback.setRating(request.rating());
        feedback.setFoodRating(request.foodRating());
        feedback.setServiceRating(request.serviceRating());
        feedback.setDeliveryRating(request.deliveryRating());
        feedback.setComment(request.comment() == null || request.comment().isBlank() ? null : request.comment().trim());
        feedbackRepository.save(feedback);
        FeedbackView view = toView(feedback);
        if (feedback.getRating() <= LOW_RATING) {
            messagingTemplate.convertAndSend("/topic/feedback.alerts", view);
            eventPublisher.publishEvent(new LowRatingEvent(view));
        }
        return view;
    }

    private List<OrderFeedbackEntity> range(LocalDate from, LocalDate to) {
        Instant start = from == null ? Instant.EPOCH : from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = to == null ? Instant.parse("9999-12-31T00:00:00Z") : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return feedbackRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(start, end);
    }

    private static BigDecimal average(List<OrderFeedbackEntity> rows, Function<OrderFeedbackEntity, Integer> field) {
        List<Integer> values = rows.stream().map(field).filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return null;
        }
        return BigDecimal.valueOf(values.stream().mapToInt(Integer::intValue).sum())
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private FeedbackView toView(OrderFeedbackEntity e) {
        String customerName = e.getCustomerUserId() == null ? e.getGuestName()
                : userRepository.findById(e.getCustomerUserId()).map(UserEntity::getName).orElse(null);
        return new FeedbackView(e.getId(), e.getOrderId(), e.getCustomerUserId(), customerName, e.getRating(), e.getFoodRating(),
                e.getServiceRating(), e.getDeliveryRating(), e.getComment(), e.getResponse(), e.getRespondedAt(), e.getCreatedAt());
    }

    public record GuestRatingPrompt(UUID orderId, String orderNumber, OrderStatus status, boolean rateable, boolean rated,
                                    Instant servedAt, Instant rateUntil) {}

    public record FeedbackRequest(@Min(1) @Max(5) int rating,
                                  @Min(1) @Max(5) Integer foodRating,
                                  @Min(1) @Max(5) Integer serviceRating,
                                  @Min(1) @Max(5) Integer deliveryRating,
                                  @Size(max = 2000) String comment,
                                  @Size(max = 120) String guestName) {}

    public record RespondRequest(@NotBlank @Size(max = 2000) String response) {}

    public record FeedbackView(UUID id, UUID orderId, UUID customerUserId, String customerName, int rating, Integer foodRating,
                               Integer serviceRating, Integer deliveryRating, String comment, String response,
                               Instant respondedAt, Instant createdAt) {}

    public record Summary(long count, BigDecimal averageRating, BigDecimal averageFood, BigDecimal averageService,
                          BigDecimal averageDelivery, Map<Integer, Long> distribution, long lowRatings, long responded) {}

    public record LowRatingEvent(FeedbackView feedback) {}
}
