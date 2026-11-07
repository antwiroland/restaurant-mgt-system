package com.restaurantmanager.core.delivery;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.notification.whatsapp.WhatsAppService;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderRealtimePublisher;
import com.restaurantmanager.core.order.OrderRepository;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.order.OrderType;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Delivery zones and fees, rider assignment, rider workflow (pickup, delivered) and live rider location.
 */
@Service
public class DeliveryService {
    private static final Set<OrderStatus> ASSIGNABLE = Set.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY);
    private static final Set<OrderStatus> RIDER_ACTIVE = Set.of(OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.OUT_FOR_DELIVERY);

    private final DeliveryZoneRepository zoneRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderRealtimePublisher orderRealtimePublisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final WhatsAppService whatsAppService;

    public DeliveryService(DeliveryZoneRepository zoneRepository,
                           OrderRepository orderRepository,
                           UserRepository userRepository,
                           OrderRealtimePublisher orderRealtimePublisher,
                           SimpMessagingTemplate messagingTemplate,
                           WhatsAppService whatsAppService) {
        this.zoneRepository = zoneRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.orderRealtimePublisher = orderRealtimePublisher;
        this.messagingTemplate = messagingTemplate;
        this.whatsAppService = whatsAppService;
    }

    // ------------------------------------------------------------ zones & pricing

    @Transactional(readOnly = true)
    public List<ZoneResponse> activeZones() {
        return zoneRepository.findByActiveTrueOrderByNameAsc().stream().map(ZoneResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> allZones() {
        return zoneRepository.findAllByOrderByNameAsc().stream().map(ZoneResponse::from).toList();
    }

    @Transactional
    public ZoneResponse saveZone(UUID id, ZoneRequest request) {
        DeliveryZoneEntity zone = id == null ? new DeliveryZoneEntity()
                : zoneRepository.findById(id).orElseThrow(() -> new ApiException(404, "Delivery zone not found"));
        zone.setName(request.name().trim());
        zone.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
        zone.setBranchId(request.branchId());
        zone.setFee(request.fee());
        zone.setMinOrderAmount(request.minOrderAmount() == null ? BigDecimal.ZERO : request.minOrderAmount());
        zone.setEstimatedMinutes(request.estimatedMinutes() == null ? 45 : request.estimatedMinutes());
        if (request.active() != null) {
            zone.setActive(request.active());
        }
        return ZoneResponse.from(zoneRepository.save(zone));
    }

    @Transactional
    public ZoneResponse setZoneActive(UUID id, boolean active) {
        DeliveryZoneEntity zone = zoneRepository.findById(id).orElseThrow(() -> new ApiException(404, "Delivery zone not found"));
        zone.setActive(active);
        return ZoneResponse.from(zoneRepository.save(zone));
    }

    /**
     * Applies the delivery zone to a new delivery order: fee, minimum order and ETA.
     * When zones are configured, delivery orders must choose one.
     */
    @Transactional(readOnly = true)
    public void applyZone(OrderEntity order, UUID zoneId) {
        if (order.getType() != OrderType.DELIVERY) {
            return;
        }
        if (zoneId == null) {
            if (zoneRepository.existsByActiveTrue()) {
                throw new ApiException(400, "deliveryZoneId is required for delivery orders");
            }
            return;
        }
        DeliveryZoneEntity zone = zoneRepository.findById(zoneId)
                .filter(DeliveryZoneEntity::isActive)
                .orElseThrow(() -> new ApiException(400, "Delivery zone not available"));
        if (order.getSubtotal().compareTo(zone.getMinOrderAmount()) < 0) {
            throw new ApiException(400, "Minimum order for " + zone.getName() + " is GHS " + zone.getMinOrderAmount());
        }
        order.setDeliveryZoneId(zone.getId());
        order.setDeliveryFee(zone.getFee());
        if (order.getEstimatedDeliveryTime() == null) {
            order.setEstimatedDeliveryTime(Instant.now().plusSeconds(zone.getEstimatedMinutes() * 60L));
        }
    }

    // ------------------------------------------------------------ riders

    @Transactional(readOnly = true)
    public List<RiderResponse> riders() {
        return userRepository.findByRoleAndActiveTrueOrderByNameAsc(Role.RIDER).stream()
                .map(rider -> new RiderResponse(rider.getId(), rider.getName(), rider.getPhone(),
                        orderRepository.countByRiderUserIdAndStatusIn(rider.getId(), RIDER_ACTIVE)))
                .toList();
    }

    @Transactional
    public DeliveryView assignRider(UUID orderId, UUID riderId, UserPrincipal principal) {
        OrderEntity order = requireDeliveryOrder(orderId);
        assertStaffBranch(order, principal);
        if (!ASSIGNABLE.contains(order.getStatus())) {
            throw new ApiException(409, "Rider can only be assigned before the order is out for delivery");
        }
        UserEntity rider = userRepository.findById(riderId)
                .filter(user -> user.getRole() == Role.RIDER && user.isActive())
                .orElseThrow(() -> new ApiException(400, "Selected user is not an active rider"));
        order.setRiderUserId(rider.getId());
        order.setRiderAssignedAt(Instant.now());
        orderRepository.save(order);
        publishDeliveryUpdate(order, rider);
        return toView(order, rider);
    }

    @Transactional(readOnly = true)
    public List<DeliveryView> riderQueue(UserPrincipal principal) {
        UserEntity rider = userRepository.findById(principal.userId()).orElseThrow(() -> new ApiException(401, "User not found"));
        return orderRepository.findByRiderUserIdAndStatusInOrderByCreatedAtAsc(principal.userId(), RIDER_ACTIVE).stream()
                .map(order -> toView(order, rider))
                .toList();
    }

    @Transactional
    public DeliveryView markPickedUp(UUID orderId, UserPrincipal principal) {
        OrderEntity order = requireRiderOrder(orderId, principal);
        if (order.getStatus() != OrderStatus.READY) {
            throw new ApiException(409, "Order must be READY before pickup");
        }
        OrderStatus previous = order.getStatus();
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        orderRepository.save(order);
        orderRealtimePublisher.publishOrderStatusChanged(order.getId(), previous, order.getStatus(), null);
        UserEntity rider = userRepository.findById(principal.userId()).orElse(null);
        notifyCustomer(order, "Your order is on the way" + (rider == null ? "" : " with " + rider.getName()));
        publishDeliveryUpdate(order, rider);
        return toView(order, rider);
    }

    @Transactional
    public DeliveryView markDelivered(UUID orderId, UserPrincipal principal) {
        OrderEntity order = requireRiderOrder(orderId, principal);
        if (order.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
            throw new ApiException(409, "Order must be out for delivery");
        }
        OrderStatus previous = order.getStatus();
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(Instant.now());
        orderRepository.save(order);
        orderRealtimePublisher.publishOrderStatusChanged(order.getId(), previous, order.getStatus(), null);
        notifyCustomer(order, "Your order has been delivered. Enjoy!");
        UserEntity rider = userRepository.findById(principal.userId()).orElse(null);
        publishDeliveryUpdate(order, rider);
        return toView(order, rider);
    }

    @Transactional
    public DeliveryView updateLocation(UUID orderId, double latitude, double longitude, UserPrincipal principal) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new ApiException(400, "Invalid coordinates");
        }
        OrderEntity order = requireRiderOrder(orderId, principal);
        if (order.getStatus() != OrderStatus.OUT_FOR_DELIVERY && order.getStatus() != OrderStatus.READY) {
            throw new ApiException(409, "Location is only shared for orders being delivered");
        }
        order.setRiderLatitude(latitude);
        order.setRiderLongitude(longitude);
        order.setRiderLocationAt(Instant.now());
        orderRepository.save(order);
        UserEntity rider = userRepository.findById(principal.userId()).orElse(null);
        publishDeliveryUpdate(order, rider);
        return toView(order, rider);
    }

    @Transactional(readOnly = true)
    public DeliveryView tracking(UUID orderId, UserPrincipal principal) {
        OrderEntity order = requireDeliveryOrder(orderId);
        if (principal.role() == Role.CUSTOMER
                && (order.getCustomerUserId() == null || !order.getCustomerUserId().equals(principal.userId()))) {
            throw new ApiException(403, "Forbidden");
        }
        if (principal.role() == Role.RIDER && !principal.userId().equals(order.getRiderUserId())) {
            throw new ApiException(403, "Forbidden");
        }
        UserEntity rider = order.getRiderUserId() == null ? null : userRepository.findById(order.getRiderUserId()).orElse(null);
        return toView(order, rider);
    }

    // ------------------------------------------------------------ helpers

    private OrderEntity requireDeliveryOrder(UUID orderId) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow(() -> new ApiException(404, "Order not found"));
        if (order.getType() != OrderType.DELIVERY) {
            throw new ApiException(400, "Not a delivery order");
        }
        return order;
    }

    private OrderEntity requireRiderOrder(UUID orderId, UserPrincipal principal) {
        OrderEntity order = requireDeliveryOrder(orderId);
        if (!principal.userId().equals(order.getRiderUserId())) {
            throw new ApiException(403, "Order is not assigned to you");
        }
        return order;
    }

    private void assertStaffBranch(OrderEntity order, UserPrincipal principal) {
        boolean scoped = (principal.role() == Role.MANAGER || principal.role() == Role.CASHIER) && principal.branchId() != null;
        if (scoped && order.getBranch() != null && !principal.branchId().equals(order.getBranch().getId())) {
            throw new ApiException(403, "Forbidden");
        }
    }

    private void notifyCustomer(OrderEntity order, String message) {
        if (order.getCustomerUserId() == null) {
            return;
        }
        userRepository.findById(order.getCustomerUserId())
                .map(UserEntity::getPhone)
                .filter(phone -> phone != null && !phone.isBlank())
                .ifPresent(phone -> whatsAppService.sendStatusUpdate(phone, message));
    }

    private void publishDeliveryUpdate(OrderEntity order, UserEntity rider) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "DELIVERY_UPDATED");
        payload.put("orderId", order.getId());
        payload.put("status", order.getStatus().name());
        payload.put("riderName", rider == null ? null : rider.getName());
        payload.put("latitude", order.getRiderLatitude());
        payload.put("longitude", order.getRiderLongitude());
        payload.put("locationAt", order.getRiderLocationAt() == null ? null : order.getRiderLocationAt().toString());
        payload.put("updatedAt", Instant.now().toString());
        messagingTemplate.convertAndSend("/topic/orders." + order.getId() + ".delivery", payload);
        messagingTemplate.convertAndSend("/topic/deliveries", payload);
    }

    private DeliveryView toView(OrderEntity order, UserEntity rider) {
        String customerPhone = order.getCustomerUserId() == null ? null
                : userRepository.findById(order.getCustomerUserId()).map(UserEntity::getPhone).orElse(null);
        return new DeliveryView(order.getId(), order.getStatus(), order.getDeliveryAddress(), order.getTotal(),
                order.getDeliveryFee(), order.getDeliveryZoneId(), order.getEstimatedDeliveryTime(),
                rider == null ? null : rider.getId(), rider == null ? null : rider.getName(), rider == null ? null : rider.getPhone(),
                order.getRiderAssignedAt(), order.getRiderLatitude(), order.getRiderLongitude(), order.getRiderLocationAt(),
                order.getDeliveredAt(), customerPhone, order.getNotes(), order.getCreatedAt());
    }

    public record ZoneRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 120) String name,
                              @jakarta.validation.constraints.Size(max = 500) String description,
                              UUID branchId,
                              @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.00") BigDecimal fee,
                              @jakarta.validation.constraints.DecimalMin("0.00") BigDecimal minOrderAmount,
                              @jakarta.validation.constraints.Min(5) @jakarta.validation.constraints.Max(600) Integer estimatedMinutes,
                              Boolean active) {}

    public record ZoneResponse(UUID id, String name, String description, UUID branchId, BigDecimal fee,
                               BigDecimal minOrderAmount, int estimatedMinutes, boolean active) {
        static ZoneResponse from(DeliveryZoneEntity zone) {
            return new ZoneResponse(zone.getId(), zone.getName(), zone.getDescription(), zone.getBranchId(), zone.getFee(),
                    zone.getMinOrderAmount(), zone.getEstimatedMinutes(), zone.isActive());
        }
    }

    public record RiderResponse(UUID id, String name, String phone, long activeDeliveries) {}

    public record DeliveryView(UUID orderId, OrderStatus status, String deliveryAddress, BigDecimal total, BigDecimal deliveryFee,
                               UUID deliveryZoneId, Instant estimatedDeliveryTime, UUID riderId, String riderName,
                               String riderPhone, Instant riderAssignedAt, Double riderLatitude, Double riderLongitude,
                               Instant riderLocationAt, Instant deliveredAt, String customerPhone, String notes,
                               Instant createdAt) {}
}
