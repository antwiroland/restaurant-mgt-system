package com.restaurantmanager.core.order;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.OverrideActionType;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.common.TokenType;
import com.restaurantmanager.core.config.CacheConfig;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.menu.MenuItemRepository;
import com.restaurantmanager.core.order.dto.GroupAddItemsRequest;
import com.restaurantmanager.core.order.dto.GroupCartItemResponse;
import com.restaurantmanager.core.order.dto.GroupCreateSessionRequest;
import com.restaurantmanager.core.order.dto.GroupFinalizeRequest;
import com.restaurantmanager.core.order.dto.GroupJoinRequest;
import com.restaurantmanager.core.order.dto.GroupJoinResponse;
import com.restaurantmanager.core.order.dto.GroupParticipantCartResponse;
import com.restaurantmanager.core.order.dto.GroupParticipantResponse;
import com.restaurantmanager.core.order.dto.GroupSessionResponse;
import com.restaurantmanager.core.order.dto.GroupViewResponse;
import com.restaurantmanager.core.order.dto.OrderCancelRequest;
import com.restaurantmanager.core.order.dto.OrderCreateItemRequest;
import com.restaurantmanager.core.order.dto.OrderCreateRequest;
import com.restaurantmanager.core.order.dto.OrderItemResponse;
import com.restaurantmanager.core.order.dto.OrderResponse;
import com.restaurantmanager.core.order.dto.OrderStatusUpdateRequest;
import com.restaurantmanager.core.security.JwtService;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.RestaurantTableRepository;
import com.restaurantmanager.core.table.TableStatus;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantTableRepository tableRepository;
    private final JwtService jwtService;
    private final GroupOrderSessionRepository groupOrderSessionRepository;
    private final GroupSessionParticipantRepository groupSessionParticipantRepository;
    private final GroupSessionItemRepository groupSessionItemRepository;
    private final UserRepository userRepository;
    private final OrderRealtimePublisher realtimePublisher;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        MenuItemRepository menuItemRepository,
                        RestaurantTableRepository tableRepository,
                        JwtService jwtService,
                        GroupOrderSessionRepository groupOrderSessionRepository,
                        GroupSessionParticipantRepository groupSessionParticipantRepository,
                        GroupSessionItemRepository groupSessionItemRepository,
                        UserRepository userRepository,
                        OrderRealtimePublisher realtimePublisher) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.menuItemRepository = menuItemRepository;
        this.tableRepository = tableRepository;
        this.jwtService = jwtService;
        this.groupOrderSessionRepository = groupOrderSessionRepository;
        this.groupSessionParticipantRepository = groupSessionParticipantRepository;
        this.groupSessionItemRepository = groupSessionItemRepository;
        this.userRepository = userRepository;
        this.realtimePublisher = realtimePublisher;
    }

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request, UserPrincipal principal) {
        return createOrderInternal(request, principal, null);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(UserPrincipal principal, LocalDate from, LocalDate to,
                                          OrderStatus status, OrderType type) {
        return orderRepository.findAll().stream()
                .filter(order -> principal.role() != Role.CUSTOMER
                        || (order.getCustomerUserId() != null && order.getCustomerUserId().equals(principal.userId())))
                .filter(order -> status == null || order.getStatus() == status)
                .filter(order -> type == null || order.getType() == type)
                .filter(order -> {
                    LocalDate date = order.getCreatedAt().atOffset(ZoneOffset.UTC).toLocalDate();
                    if (from != null && date.isBefore(from)) {
                        return false;
                    }
                    return to == null || !date.isAfter(to);
                })
                .sorted(Comparator.comparing(OrderEntity::getCreatedAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID id, UserPrincipal principal) {
        OrderEntity order = orderRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Order not found"));
        assertOrderAccess(order, principal);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse updateStatus(UUID id, OrderStatusUpdateRequest request) {
        OrderEntity order = orderRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Order not found"));
        if (!isValidTransition(order.getStatus(), request.status(), order.getType())) {
            throw new ApiException(400, "Invalid status transition");
        }
        OrderStatus previous = order.getStatus();
        order.setStatus(request.status());
        OrderEntity saved = orderRepository.save(order);
        realtimePublisher.publishOrderStatusChanged(saved.getId(), previous, saved.getStatus());
        return toResponse(saved);
    }

    @Transactional
    public CancelResult cancelOrder(UUID id, UserPrincipal principal, OrderCancelRequest request) {
        OrderEntity order = orderRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Order not found"));

        if (principal.role() == Role.CUSTOMER) {
            if (order.getCustomerUserId() == null || !order.getCustomerUserId().equals(principal.userId())) {
                throw new ApiException(403, "You can only cancel your own order");
            }
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return new CancelResult(false, toResponse(order));
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            validateOverrideToken(request == null ? null : request.overrideToken(), OverrideActionType.VOID);
        }

        OrderStatus previous = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(request == null ? null : blankToNull(request.reason()));
        OrderEntity saved = orderRepository.save(order);
        realtimePublisher.publishOrderStatusChanged(saved.getId(), previous, saved.getStatus());

        boolean returnBody = previous != OrderStatus.PENDING;
        return new CancelResult(returnBody, toResponse(saved));
    }

    @Transactional(readOnly = true)
    public OrderResponse findByPickupCode(String pickupCode) {
        OrderEntity order = orderRepository.findByPickupCode(pickupCode)
                .orElseThrow(() -> new ApiException(404, "Pickup code not found"));
        return toResponse(order);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.TABLES, CacheConfig.TABLE_SCAN}, allEntries = true)
    public void closeTable(UUID tableId) {
        RestaurantTableEntity table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ApiException(404, "Table not found"));
        table.setStatus(TableStatus.AVAILABLE);
        tableRepository.save(table);
    }

    @Transactional
    public GroupSessionResponse createGroupSession(GroupCreateSessionRequest request, UserPrincipal principal) {
        GroupOrderSessionEntity session = new GroupOrderSessionEntity();
        session.setSessionCode(generateSessionCode());
        session.setHostUserId(principal.userId());
        session.setStatus(GroupSessionStatus.OPEN);
        GroupOrderSessionEntity savedSession = groupOrderSessionRepository.save(session);

        GroupSessionParticipantEntity host = new GroupSessionParticipantEntity();
        host.setSession(savedSession);
        host.setUserId(principal.userId());
        host.setDisplayName(resolveDisplayName(principal.userId(), request == null ? null : request.displayName()));
        GroupSessionParticipantEntity savedHost = groupSessionParticipantRepository.save(host);

        return new GroupSessionResponse(
                savedSession.getId(),
                savedSession.getSessionCode(),
                savedSession.getStatus(),
                savedSession.getHostUserId(),
                savedHost.getId()
        );
    }

    @Transactional
    public GroupJoinResponse joinGroupSession(String code, GroupJoinRequest request, UserPrincipal principal) {
        GroupOrderSessionEntity session = groupOrderSessionRepository.findBySessionCodeIgnoreCase(code)
                .orElseThrow(() -> new ApiException(404, "Group session not found"));
        if (session.getStatus() != GroupSessionStatus.OPEN) {
            throw new ApiException(409, "Group session is not open");
        }

        GroupSessionParticipantEntity participant = groupSessionParticipantRepository
                .findBySessionIdAndUserId(session.getId(), principal.userId())
                .orElseGet(() -> {
                    GroupSessionParticipantEntity p = new GroupSessionParticipantEntity();
                    p.setSession(session);
                    p.setUserId(principal.userId());
                    p.setDisplayName(resolveDisplayName(principal.userId(), request == null ? null : request.displayName()));
                    return groupSessionParticipantRepository.save(p);
                });

        List<GroupParticipantResponse> participants = groupSessionParticipantRepository.findBySessionId(session.getId()).stream()
                .map(this::toParticipantResponse)
                .toList();

        return new GroupJoinResponse(session.getId(), participant.getId(), participants);
    }

    @Transactional
    public GroupViewResponse addGroupItems(String code, GroupAddItemsRequest request, UserPrincipal principal) {
        GroupOrderSessionEntity session = groupOrderSessionRepository.findBySessionCodeIgnoreCase(code)
                .orElseThrow(() -> new ApiException(404, "Group session not found"));
        if (session.getStatus() != GroupSessionStatus.OPEN) {
            throw new ApiException(409, "Group session is not open");
        }

        GroupSessionParticipantEntity participant = groupSessionParticipantRepository.findById(request.participantId())
                .orElseThrow(() -> new ApiException(404, "Participant not found"));
        if (!participant.getSession().getId().equals(session.getId())) {
            throw new ApiException(400, "Participant is not in this session");
        }
        if (!participant.getUserId().equals(principal.userId())) {
            throw new ApiException(403, "You can only add items for yourself");
        }

        for (OrderCreateItemRequest itemRequest : request.items()) {
            MenuItemEntity menuItem = menuItemRepository.findById(itemRequest.menuItemId())
                    .orElseThrow(() -> new ApiException(404, "Menu item not found"));
            if (!menuItem.isAvailable()) {
                throw new ApiException(400, "Menu item is unavailable");
            }

            GroupSessionItemEntity item = new GroupSessionItemEntity();
            item.setSession(session);
            item.setParticipant(participant);
            item.setMenuItem(menuItem);
            item.setQuantity(itemRequest.quantity());
            item.setNotes(blankToNull(itemRequest.notes()));
            groupSessionItemRepository.save(item);
        }

        GroupViewResponse response = toGroupViewResponse(session);
        realtimePublisher.publishGroupCartUpdated(session.getSessionCode(), participant.getId(), response.groupTotal());
        return response;
    }

    @Transactional(readOnly = true)
    public GroupViewResponse viewGroupSession(String code) {
        GroupOrderSessionEntity session = groupOrderSessionRepository.findBySessionCodeIgnoreCase(code)
                .orElseThrow(() -> new ApiException(404, "Group session not found"));
        return toGroupViewResponse(session);
    }

    @Transactional
    public OrderResponse finalizeGroupOrder(String code, GroupFinalizeRequest request, UserPrincipal principal) {
        GroupOrderSessionEntity session = groupOrderSessionRepository.findBySessionCodeIgnoreCase(code)
                .orElseThrow(() -> new ApiException(404, "Group session not found"));
        if (!session.getHostUserId().equals(principal.userId())) {
            throw new ApiException(403, "Only host can finalize group order");
        }
        if (session.getStatus() != GroupSessionStatus.OPEN) {
            throw new ApiException(409, "Group session is not open");
        }

        List<GroupSessionItemEntity> sessionItems = groupSessionItemRepository.findBySessionId(session.getId());
        if (sessionItems.isEmpty()) {
            throw new ApiException(400, "Group cart is empty");
        }

        OrderCreateRequest createRequest = new OrderCreateRequest(
                request.type(),
                request.tableId(),
                request.tableToken(),
                sessionItems.stream().map(item -> new OrderCreateItemRequest(
                        item.getMenuItem().getId(),
                        item.getQuantity(),
                        item.getNotes()
                )).toList(),
                request.deliveryAddress(),
                request.pickupTime(),
                request.estimatedDeliveryTime(),
                session.getId(),
                request.notes()
        );

        Map<Integer, UUID> participantByIndex = new HashMap<>();
        for (int i = 0; i < sessionItems.size(); i++) {
            participantByIndex.put(i, sessionItems.get(i).getParticipant().getId());
        }

        OrderResponse response = createOrderInternal(createRequest, principal, participantByIndex);
        session.setStatus(GroupSessionStatus.COMPLETED);
        groupOrderSessionRepository.save(session);
        return response;
    }

    private OrderResponse createOrderInternal(OrderCreateRequest request, UserPrincipal principal, Map<Integer, UUID> participantByIndex) {
        OrderEntity order = new OrderEntity();
        order.setType(request.type());
        order.setStatus(OrderStatus.PENDING);
        order.setNotes(blankToNull(request.notes()));

        if (principal.role() == Role.CUSTOMER) {
            order.setCustomerUserId(principal.userId());
        } else {
            order.setCreatedByUserId(principal.userId());
            if (request.groupSessionId() == null && request.type() != OrderType.DINE_IN) {
                order.setCustomerUserId(null);
            }
        }

        if (request.groupSessionId() != null) {
            GroupOrderSessionEntity session = groupOrderSessionRepository.findById(request.groupSessionId())
                    .orElseThrow(() -> new ApiException(404, "Group session not found"));
            order.setGroupSession(session);
            if (order.getCustomerUserId() == null) {
                order.setCustomerUserId(session.getHostUserId());
            }
        }

        if (request.type() == OrderType.DELIVERY) {
            if (request.deliveryAddress() == null || request.deliveryAddress().isBlank()) {
                throw new ApiException(400, "deliveryAddress is required for delivery orders");
            }
            order.setDeliveryAddress(request.deliveryAddress().trim());
            order.setEstimatedDeliveryTime(request.estimatedDeliveryTime());
        } else if (request.type() == OrderType.PICKUP) {
            order.setPickupTime(request.pickupTime());
            order.setPickupCode(generatePickupCode());
        } else if (request.type() == OrderType.DINE_IN) {
            RestaurantTableEntity table = resolveTable(request.tableId(), request.tableToken());
            table.setStatus(TableStatus.OCCUPIED);
            tableRepository.save(table);
            order.setTable(table);
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItemEntity> items = new ArrayList<>();
        for (int i = 0; i < request.items().size(); i++) {
            OrderCreateItemRequest itemRequest = request.items().get(i);
            MenuItemEntity menuItem = menuItemRepository.findById(itemRequest.menuItemId())
                    .orElseThrow(() -> new ApiException(404, "Menu item not found"));
            if (principal.role() == Role.CUSTOMER && !menuItem.isAvailable()) {
                throw new ApiException(400, "Menu item is unavailable");
            }
            OrderItemEntity item = new OrderItemEntity();
            item.setMenuItem(menuItem);
            item.setNameSnapshot(menuItem.getName());
            item.setPriceSnapshot(menuItem.getPrice());
            item.setQuantity(itemRequest.quantity());
            item.setNotes(blankToNull(itemRequest.notes()));
            if (participantByIndex != null) {
                item.setParticipantId(participantByIndex.get(i));
            }
            subtotal = subtotal.add(menuItem.getPrice().multiply(BigDecimal.valueOf(itemRequest.quantity())));
            items.add(item);
        }
        order.setSubtotal(subtotal);
        order.setTotal(subtotal);
        OrderEntity saved = orderRepository.save(order);

        for (OrderItemEntity item : items) {
            item.setOrder(saved);
        }
        orderItemRepository.saveAll(items);

        realtimePublisher.publishOrderCreated(saved.getId(), saved.getType(), saved.getStatus(), saved.getTotal());
        return toResponse(saved);
    }

    private RestaurantTableEntity resolveTable(UUID tableId, String tableToken) {
        if (tableId != null) {
            return tableRepository.findById(tableId)
                    .orElseThrow(() -> new ApiException(404, "Table not found"));
        }
        if (tableToken != null && !tableToken.isBlank()) {
            return tableRepository.findByQrToken(tableToken.trim())
                    .orElseThrow(() -> new ApiException(404, "Table token not found"));
        }
        throw new ApiException(400, "tableId or tableToken is required for dine-in order");
    }

    private void validateOverrideToken(String overrideToken, OverrideActionType expectedAction) {
        if (overrideToken == null || overrideToken.isBlank()) {
            throw new ApiException(403, "Override token required");
        }

        Claims claims;
        try {
            claims = jwtService.parse(overrideToken);
        } catch (ExpiredJwtException ex) {
            throw new ApiException(401, "Override token expired");
        }

        if (jwtService.tokenType(claims) != TokenType.OVERRIDE) {
            throw new ApiException(401, "Invalid override token");
        }
        if (jwtService.actionType(claims) != expectedAction) {
            throw new ApiException(403, "Override token not valid for this action");
        }
    }

    private boolean isValidTransition(OrderStatus previous, OrderStatus next, OrderType orderType) {
        return switch (previous) {
            case PENDING -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.PREPARING || next == OrderStatus.CANCELLED;
            case PREPARING -> next == OrderStatus.READY;
            case READY -> next == OrderStatus.COMPLETED
                    || (orderType == OrderType.DELIVERY && next == OrderStatus.OUT_FOR_DELIVERY);
            case OUT_FOR_DELIVERY -> next == OrderStatus.DELIVERED;
            case DELIVERED -> next == OrderStatus.COMPLETED;
            default -> false;
        };
    }

    private OrderResponse toResponse(OrderEntity order) {
        List<OrderItemResponse> itemResponses = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(item -> new OrderItemResponse(
                        item.getId(),
                        item.getMenuItem().getId(),
                        item.getParticipantId(),
                        item.getNameSnapshot(),
                        item.getPriceSnapshot(),
                        item.getQuantity(),
                        item.getNotes()
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerUserId(),
                order.getType(),
                order.getStatus(),
                order.getTable() == null ? null : order.getTable().getId(),
                order.getTable() == null ? null : order.getTable().getNumber(),
                order.getDeliveryAddress(),
                order.getPickupTime(),
                order.getPickupCode(),
                order.getEstimatedDeliveryTime(),
                order.getGroupSession() == null ? null : order.getGroupSession().getId(),
                order.getNotes(),
                order.getCancelReason(),
                order.getSubtotal(),
                order.getTotal(),
                order.getCreatedAt(),
                itemResponses
        );
    }

    private GroupViewResponse toGroupViewResponse(GroupOrderSessionEntity session) {
        List<GroupSessionParticipantEntity> participants = groupSessionParticipantRepository.findBySessionId(session.getId());
        List<GroupSessionItemEntity> allItems = groupSessionItemRepository.findBySessionId(session.getId());

        Map<UUID, List<GroupSessionItemEntity>> itemsByParticipant = new HashMap<>();
        for (GroupSessionItemEntity item : allItems) {
            itemsByParticipant.computeIfAbsent(item.getParticipant().getId(), key -> new ArrayList<>()).add(item);
        }

        List<GroupParticipantCartResponse> participantResponses = participants.stream()
                .map(participant -> {
                    List<GroupSessionItemEntity> participantItems = itemsByParticipant.getOrDefault(participant.getId(), List.of());
                    List<GroupCartItemResponse> itemResponses = participantItems.stream()
                            .map(item -> new GroupCartItemResponse(
                                    item.getId(),
                                    item.getMenuItem().getId(),
                                    item.getMenuItem().getName(),
                                    item.getMenuItem().getPrice(),
                                    item.getQuantity(),
                                    item.getNotes()
                            ))
                            .toList();
                    BigDecimal subtotal = participantItems.stream()
                            .map(item -> item.getMenuItem().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new GroupParticipantCartResponse(
                            participant.getId(),
                            participant.getUserId(),
                            participant.getDisplayName(),
                            subtotal,
                            itemResponses
                    );
                })
                .toList();

        BigDecimal groupTotal = participantResponses.stream()
                .map(GroupParticipantCartResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new GroupViewResponse(
                session.getId(),
                session.getSessionCode(),
                session.getStatus(),
                groupTotal,
                participantResponses
        );
    }

    private GroupParticipantResponse toParticipantResponse(GroupSessionParticipantEntity participant) {
        return new GroupParticipantResponse(participant.getId(), participant.getUserId(), participant.getDisplayName());
    }

    private void assertOrderAccess(OrderEntity order, UserPrincipal principal) {
        if (principal.role() == Role.CUSTOMER) {
            if (order.getCustomerUserId() == null || !order.getCustomerUserId().equals(principal.userId())) {
                throw new ApiException(403, "Forbidden");
            }
        }
    }

    private String generatePickupCode() {
        String code;
        do {
            code = "PK%06d".formatted(SECURE_RANDOM.nextInt(1_000_000));
        } while (orderRepository.findByPickupCode(code).isPresent());
        return code;
    }

    private String generateSessionCode() {
        String code;
        do {
            code = "GRP-%04d".formatted(1000 + SECURE_RANDOM.nextInt(9000));
        } while (groupOrderSessionRepository.findBySessionCodeIgnoreCase(code).isPresent());
        return code;
    }

    private String resolveDisplayName(UUID userId, String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(401, "User not found"));
        return user.getName();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record CancelResult(boolean returnBody, OrderResponse order) {
    }
}
