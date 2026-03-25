package com.restaurantmanager.core.order;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.OverrideActionType;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.common.TokenType;
import com.restaurantmanager.core.config.CacheConfig;
import com.restaurantmanager.core.menu.MenuModifierGroupEntity;
import com.restaurantmanager.core.menu.MenuModifierGroupRepository;
import com.restaurantmanager.core.menu.MenuModifierOptionEntity;
import com.restaurantmanager.core.menu.MenuModifierOptionRepository;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.menu.MenuItemRepository;
import com.restaurantmanager.core.menu.ModifierSelectionType;
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
import com.restaurantmanager.core.order.dto.OrderItemModifierResponse;
import com.restaurantmanager.core.order.dto.OrderItemResponse;
import com.restaurantmanager.core.order.dto.OrderResponse;
import com.restaurantmanager.core.order.dto.OrderStatusUpdateRequest;
import com.restaurantmanager.core.order.dto.PublicTableOrderCreateRequest;
import com.restaurantmanager.core.order.dto.TableBillResponse;
import com.restaurantmanager.core.payment.PaymentRepository;
import com.restaurantmanager.core.payment.PaymentStatus;
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
    private final OrderItemModifierRepository orderItemModifierRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuModifierGroupRepository modifierGroupRepository;
    private final MenuModifierOptionRepository modifierOptionRepository;
    private final RestaurantTableRepository tableRepository;
    private final JwtService jwtService;
    private final GroupOrderSessionRepository groupOrderSessionRepository;
    private final GroupSessionParticipantRepository groupSessionParticipantRepository;
    private final GroupSessionItemRepository groupSessionItemRepository;
    private final UserRepository userRepository;
    private final OrderRealtimePublisher realtimePublisher;
    private final PaymentRepository paymentRepository;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        OrderItemModifierRepository orderItemModifierRepository,
                        MenuItemRepository menuItemRepository,
                        MenuModifierGroupRepository modifierGroupRepository,
                        MenuModifierOptionRepository modifierOptionRepository,
                        RestaurantTableRepository tableRepository,
                        JwtService jwtService,
                        GroupOrderSessionRepository groupOrderSessionRepository,
                        GroupSessionParticipantRepository groupSessionParticipantRepository,
                        GroupSessionItemRepository groupSessionItemRepository,
                        UserRepository userRepository,
                        OrderRealtimePublisher realtimePublisher,
                        PaymentRepository paymentRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderItemModifierRepository = orderItemModifierRepository;
        this.menuItemRepository = menuItemRepository;
        this.modifierGroupRepository = modifierGroupRepository;
        this.modifierOptionRepository = modifierOptionRepository;
        this.tableRepository = tableRepository;
        this.jwtService = jwtService;
        this.groupOrderSessionRepository = groupOrderSessionRepository;
        this.groupSessionParticipantRepository = groupSessionParticipantRepository;
        this.groupSessionItemRepository = groupSessionItemRepository;
        this.userRepository = userRepository;
        this.realtimePublisher = realtimePublisher;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request, UserPrincipal principal) {
        return createOrderInternal(request, principal, null);
    }

    @Transactional
    public OrderResponse createPublicDineInOrder(PublicTableOrderCreateRequest request) {
        OrderCreateRequest internalRequest = new OrderCreateRequest(
                OrderType.DINE_IN,
                null,
                request.tableToken(),
                request.items(),
                null,
                null,
                null,
                null,
                request.notes()
        );
        return createOrderInternal(internalRequest, null, null);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(UserPrincipal principal, LocalDate from, LocalDate to,
                                          OrderStatus status, OrderType type) {
        List<OrderEntity> orders = orderRepository.findAll().stream()
                .filter(order -> principal.role() != Role.CUSTOMER
                        || (order.getCustomerUserId() != null && order.getCustomerUserId().equals(principal.userId())))
                .filter(order -> !isBranchScopedStaff(principal)
                        || (order.getBranch() != null && order.getBranch().getId().equals(principal.branchId())))
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
                .toList();
        return toResponses(orders);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID id, UserPrincipal principal) {
        OrderEntity order = orderRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Order not found"));
        assertOrderAccess(order, principal);
        return toResponses(List.of(order)).get(0);
    }

    @Transactional
    public OrderResponse updateStatus(UUID id, OrderStatusUpdateRequest request, UserPrincipal principal) {
        OrderEntity order = orderRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Order not found"));
        assertOrderAccess(order, principal);
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
        return toResponses(List.of(order)).get(0);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.TABLES, CacheConfig.TABLE_SCAN}, allEntries = true)
    public void closeTable(UUID tableId, UserPrincipal principal) {
        RestaurantTableEntity table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ApiException(404, "Table not found"));
        assertTableAccess(table, principal);
        if (tableHasOutstandingBalance(tableId)) {
            throw new ApiException(409, "Table has outstanding balance. Settle bills or reverse table");
        }
        table.setStatus(TableStatus.AVAILABLE);
        tableRepository.save(table);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.TABLES, CacheConfig.TABLE_SCAN}, allEntries = true)
    public void reverseTable(UUID tableId, UserPrincipal principal) {
        RestaurantTableEntity table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ApiException(404, "Table not found"));
        assertTableAccess(table, principal);

        List<OrderEntity> tableOrders = orderRepository.findByTableIdOrderByCreatedAtAsc(tableId);
        Map<UUID, BigDecimal> paidByOrderId = paymentTotalsByOrderIds(tableOrders.stream().map(OrderEntity::getId).toList());
        for (OrderEntity order : tableOrders) {
            if (order.getType() != OrderType.DINE_IN || order.getStatus() == OrderStatus.CANCELLED) {
                continue;
            }
            if (isOrderSettled(order, paidByOrderId.getOrDefault(order.getId(), BigDecimal.ZERO))) {
                continue;
            }
            OrderStatus previous = order.getStatus();
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelReason("Reversed by manager");
            OrderEntity savedOrder = orderRepository.save(order);
            realtimePublisher.publishOrderStatusChanged(savedOrder.getId(), previous, savedOrder.getStatus());
        }

        table.setStatus(TableStatus.AVAILABLE);
        tableRepository.save(table);
    }

    @Transactional(readOnly = true)
    public TableBillResponse tableBillByTableId(UUID tableId, UserPrincipal principal) {
        RestaurantTableEntity table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ApiException(404, "Table not found"));
        assertTableAccess(table, principal);
        return toTableBillResponse(table, orderRepository.findByTableIdOrderByCreatedAtAsc(tableId));
    }

    @Transactional(readOnly = true)
    public TableBillResponse tableBillByTableToken(String tableToken) {
        RestaurantTableEntity table = tableRepository.findByQrToken(tableToken)
                .orElseThrow(() -> new ApiException(404, "Table token not found"));
        return toTableBillResponse(table, orderRepository.findByTable_QrTokenOrderByCreatedAtAsc(tableToken));
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
                        item.getNotes(),
                        null
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

        if (principal != null) {
            if (principal.role() == Role.CUSTOMER) {
                order.setCustomerUserId(principal.userId());
            } else {
                order.setCreatedByUserId(principal.userId());
                if (request.groupSessionId() == null && request.type() != OrderType.DINE_IN) {
                    order.setCustomerUserId(null);
                }
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

        if (principal != null && principal.role() != Role.CUSTOMER) {
            UserEntity creator = userRepository.findById(principal.userId()).orElse(null);
            if (creator != null && request.type() != OrderType.DINE_IN) {
                order.setBranch(creator.getBranch());
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
            RestaurantTableEntity table = resolveTable(request.tableId(), request.tableToken(), principal);
            table.setStatus(TableStatus.OCCUPIED);
            tableRepository.save(table);
            order.setTable(table);
            order.setBranch(table.getBranch());
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItemEntity> items = new ArrayList<>();
        Map<Integer, List<MenuModifierOptionEntity>> modifiersByIndex = new HashMap<>();
        for (int i = 0; i < request.items().size(); i++) {
            OrderCreateItemRequest itemRequest = request.items().get(i);
            MenuItemEntity menuItem = menuItemRepository.findById(itemRequest.menuItemId())
                    .orElseThrow(() -> new ApiException(404, "Menu item not found"));
            if ((principal == null || principal.role() == Role.CUSTOMER) && !menuItem.isAvailable()) {
                throw new ApiException(400, "Menu item is unavailable");
            }
            OrderItemEntity item = new OrderItemEntity();
            item.setMenuItem(menuItem);
            item.setNameSnapshot(menuItem.getName());
            List<MenuModifierOptionEntity> selectedModifiers = validateAndResolveModifiers(menuItem, itemRequest.modifierOptionIds());
            modifiersByIndex.put(i, selectedModifiers);
            BigDecimal modifierDelta = selectedModifiers.stream()
                    .map(MenuModifierOptionEntity::getPriceDelta)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            item.setPriceSnapshot(menuItem.getPrice().add(modifierDelta));
            item.setQuantity(itemRequest.quantity());
            item.setNotes(blankToNull(itemRequest.notes()));
            if (participantByIndex != null) {
                item.setParticipantId(participantByIndex.get(i));
            }
            subtotal = subtotal.add(item.getPriceSnapshot().multiply(BigDecimal.valueOf(itemRequest.quantity())));
            items.add(item);
        }
        order.setSubtotal(subtotal);
        order.setTotal(subtotal);
        OrderEntity saved = orderRepository.save(order);

        for (OrderItemEntity item : items) {
            item.setOrder(saved);
        }
        List<OrderItemEntity> savedItems = orderItemRepository.saveAll(items);

        List<OrderItemModifierEntity> modifierRows = new ArrayList<>();
        for (int i = 0; i < savedItems.size(); i++) {
            OrderItemEntity savedItem = savedItems.get(i);
            List<MenuModifierOptionEntity> options = modifiersByIndex.getOrDefault(i, List.of());
            for (MenuModifierOptionEntity option : options) {
                OrderItemModifierEntity row = new OrderItemModifierEntity();
                row.setOrderItem(savedItem);
                row.setModifierOption(option);
                row.setGroupNameSnapshot(option.getGroup().getName());
                row.setOptionNameSnapshot(option.getName());
                row.setPriceDeltaSnapshot(option.getPriceDelta());
                modifierRows.add(row);
            }
        }
        if (!modifierRows.isEmpty()) {
            orderItemModifierRepository.saveAll(modifierRows);
        }

        realtimePublisher.publishOrderCreated(saved.getId(), saved.getType(), saved.getStatus(), saved.getTotal());
        return toResponse(saved);
    }

    private RestaurantTableEntity resolveTable(UUID tableId, String tableToken, UserPrincipal principal) {
        if (tableId != null) {
            RestaurantTableEntity table = tableRepository.findById(tableId)
                    .orElseThrow(() -> new ApiException(404, "Table not found"));
            assertTableAccess(table, principal);
            return table;
        }
        if (tableToken != null && !tableToken.isBlank()) {
            RestaurantTableEntity table = tableRepository.findByQrToken(tableToken.trim())
                    .orElseThrow(() -> new ApiException(404, "Table token not found"));
            assertTableAccess(table, principal);
            return table;
        }
        throw new ApiException(400, "tableId or tableToken is required for dine-in order");
    }

    private List<MenuModifierOptionEntity> validateAndResolveModifiers(MenuItemEntity menuItem, List<UUID> modifierOptionIds) {
        List<MenuModifierGroupEntity> groups = modifierGroupRepository.findByMenuItem_IdAndActiveTrueOrderByDisplayOrderAsc(menuItem.getId());
        if (groups.isEmpty()) {
            return List.of();
        }

        List<UUID> optionIds = modifierOptionIds == null ? List.of() : modifierOptionIds.stream().distinct().toList();
        if (optionIds.isEmpty()) {
            boolean missingRequired = groups.stream().anyMatch(MenuModifierGroupEntity::isRequired);
            if (missingRequired) {
                throw new ApiException(400, "Required modifiers missing for menu item");
            }
            return List.of();
        }

        List<MenuModifierOptionEntity> options = modifierOptionRepository.findAllById(optionIds);
        if (options.size() != optionIds.size()) {
            throw new ApiException(404, "Modifier option not found");
        }

        Map<UUID, Integer> selectionCountByGroup = new HashMap<>();
        for (MenuModifierOptionEntity option : options) {
            if (!option.isActive() || !option.getGroup().isActive()) {
                throw new ApiException(400, "Modifier option is unavailable");
            }
            if (!option.getGroup().getMenuItem().getId().equals(menuItem.getId())) {
                throw new ApiException(400, "Modifier does not belong to menu item");
            }
            UUID groupId = option.getGroup().getId();
            selectionCountByGroup.put(groupId, selectionCountByGroup.getOrDefault(groupId, 0) + 1);
        }

        for (MenuModifierGroupEntity group : groups) {
            int selectedCount = selectionCountByGroup.getOrDefault(group.getId(), 0);
            if (group.isRequired() && selectedCount == 0) {
                throw new ApiException(400, "Required modifier group missing: " + group.getName());
            }
            if (group.getSelectionType() == ModifierSelectionType.SINGLE && selectedCount > 1) {
                throw new ApiException(400, "Only one option allowed for modifier group: " + group.getName());
            }
            if (group.getMinSelect() != null && selectedCount < group.getMinSelect()) {
                throw new ApiException(400, "Minimum selections not met for group: " + group.getName());
            }
            if (group.getMaxSelect() != null && selectedCount > group.getMaxSelect()) {
                throw new ApiException(400, "Too many selections for group: " + group.getName());
            }
        }
        return options;
    }

    @Transactional(readOnly = true)
    public boolean tableHasOutstandingBalance(UUID tableId) {
        List<OrderEntity> tableOrders = orderRepository.findByTableIdOrderByCreatedAtAsc(tableId);
        Map<UUID, BigDecimal> paidByOrderId = paymentTotalsByOrderIds(tableOrders.stream().map(OrderEntity::getId).toList());
        for (OrderEntity order : tableOrders) {
            if (order.getType() != OrderType.DINE_IN || order.getStatus() == OrderStatus.CANCELLED) {
                continue;
            }
            if (!isOrderSettled(order, paidByOrderId.getOrDefault(order.getId(), BigDecimal.ZERO))) {
                return true;
            }
        }
        return false;
    }

    private boolean isOrderSettled(OrderEntity order) {
        BigDecimal paid = paymentTotalsByOrderIds(List.of(order.getId())).getOrDefault(order.getId(), BigDecimal.ZERO);
        return isOrderSettled(order, paid);
    }

    private boolean isOrderSettled(OrderEntity order, BigDecimal paidTotal) {
        return paidTotal.compareTo(order.getTotal()) >= 0;
    }

    private Map<UUID, BigDecimal> paymentTotalsByOrderIds(List<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BigDecimal> totals = new HashMap<>();
        paymentRepository.findByOrderIdInAndStatus(orderIds, PaymentStatus.SUCCESS).forEach(payment ->
                totals.merge(payment.getOrder().getId(), payment.getAmount(), BigDecimal::add));
        return totals;
    }

    private TableBillResponse toTableBillResponse(RestaurantTableEntity table, List<OrderEntity> orders) {
        BigDecimal totalOrdered = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        int activeOrders = 0;
        Map<UUID, BigDecimal> paidByOrderId = paymentTotalsByOrderIds(orders.stream().map(OrderEntity::getId).toList());

        for (OrderEntity order : orders) {
            if (order.getType() != OrderType.DINE_IN || order.getStatus() == OrderStatus.CANCELLED) {
                continue;
            }
            activeOrders++;
            totalOrdered = totalOrdered.add(order.getTotal());
            BigDecimal orderPaid = paidByOrderId.getOrDefault(order.getId(), BigDecimal.ZERO);
            totalPaid = totalPaid.add(orderPaid);
        }

        BigDecimal outstanding = totalOrdered.subtract(totalPaid);
        if (outstanding.signum() < 0) {
            outstanding = BigDecimal.ZERO;
        }

        return new TableBillResponse(
                table.getId(),
                table.getNumber(),
                table.getStatus(),
                activeOrders,
                totalOrdered,
                totalPaid,
                outstanding
        );
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
        return toResponses(List.of(order)).get(0);
    }

    private List<OrderResponse> toResponses(List<OrderEntity> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }

        List<UUID> orderIds = orders.stream().map(OrderEntity::getId).toList();
        List<OrderItemEntity> allItems = orderItemRepository.findByOrderIdIn(orderIds);
        Map<UUID, List<OrderItemEntity>> itemsByOrderId = new HashMap<>();
        for (OrderItemEntity item : allItems) {
            itemsByOrderId.computeIfAbsent(item.getOrder().getId(), ignored -> new ArrayList<>()).add(item);
        }

        List<UUID> itemIds = allItems.stream().map(OrderItemEntity::getId).toList();
        Map<UUID, List<OrderItemModifierResponse>> modifiersByItemId = new HashMap<>();
        if (!itemIds.isEmpty()) {
            orderItemModifierRepository.findByOrderItem_IdInOrderByCreatedAtAsc(itemIds).forEach(modifier -> {
                List<OrderItemModifierResponse> bucket = modifiersByItemId.computeIfAbsent(
                        modifier.getOrderItem().getId(), ignored -> new ArrayList<>());
                bucket.add(new OrderItemModifierResponse(
                        modifier.getId(),
                        modifier.getGroupNameSnapshot(),
                        modifier.getOptionNameSnapshot(),
                        modifier.getPriceDeltaSnapshot()
                ));
            });
        }

        return orders.stream().map(order -> {
            List<OrderItemResponse> itemResponses = itemsByOrderId.getOrDefault(order.getId(), List.of()).stream()
                    .map(item -> new OrderItemResponse(
                            item.getId(),
                            item.getMenuItem().getId(),
                            item.getParticipantId(),
                            item.getNameSnapshot(),
                            item.getPriceSnapshot(),
                            item.getQuantity(),
                            item.getNotes(),
                            modifiersByItemId.getOrDefault(item.getId(), List.of())
                    ))
                    .toList();

            return new OrderResponse(
                    order.getId(),
                    order.getCustomerUserId(),
                    order.getType(),
                    order.getStatus(),
                    order.getBranch() == null ? null : order.getBranch().getId(),
                    order.getBranch() == null ? null : order.getBranch().getName(),
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
        }).toList();
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
            return;
        }
        if (isBranchScopedStaff(principal)) {
            UUID branchId = principal.branchId();
            if (order.getBranch() == null || !branchId.equals(order.getBranch().getId())) {
                throw new ApiException(403, "Forbidden");
            }
        }
    }

    private void assertTableAccess(RestaurantTableEntity table, UserPrincipal principal) {
        if (!isBranchScopedStaff(principal)) {
            return;
        }
        UUID branchId = principal.branchId();
        if (table.getBranch() == null || !branchId.equals(table.getBranch().getId())) {
            throw new ApiException(403, "Forbidden");
        }
    }

    private boolean isBranchScopedStaff(UserPrincipal principal) {
        return principal != null
                && (principal.role() == Role.MANAGER || principal.role() == Role.CASHIER)
                && principal.branchId() != null;
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
