package com.restaurantmanager.core.security;

import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.order.GroupOrderSessionRepository;
import com.restaurantmanager.core.order.GroupSessionParticipantRepository;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Who may subscribe to which STOMP topic. Everything not listed is refused.
 *
 * <ul>
 *   <li>Public (no sign-in): a table's own topics, guarded by its secret QR token
 *   ({@code /topic/public.tables.{token}.orders|requests}), the pickup board ping and the table status board.</li>
 *   <li>Floor staff (admin, manager, cashier): new orders, status changes, payments, service requests, deliveries.</li>
 *   <li>Managers and admins: inventory and low-rating alerts.</li>
 *   <li>One order ({@code /topic/orders.{id}[.payments|.delivery]}): floor staff, the order's customer, its rider.</li>
 *   <li>A group order ({@code /topic/group.{code}}): its participants and floor staff.</li>
 * </ul>
 * HR officers have no floor access, so they get none of the staff topics.
 */
@Component
public class WebSocketTopicAuthorizer {
    private static final Set<Role> FLOOR_STAFF = EnumSet.of(Role.ADMIN, Role.MANAGER, Role.CASHIER);
    private static final Set<Role> MANAGERS = EnumSet.of(Role.ADMIN, Role.MANAGER);

    private static final Pattern PUBLIC_TABLE = Pattern.compile("^/topic/public\\.tables\\.[A-Za-z0-9_-]{8,128}\\.(orders|requests)$");
    private static final Pattern ORDER = Pattern.compile(
            "^/topic/orders\\.([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(\\.payments|\\.delivery)?$");
    private static final Pattern GROUP = Pattern.compile("^/topic/group\\.([A-Za-z0-9-]{3,64})$");

    private final OrderRepository orderRepository;
    private final GroupOrderSessionRepository groupSessionRepository;
    private final GroupSessionParticipantRepository participantRepository;

    public WebSocketTopicAuthorizer(OrderRepository orderRepository,
                                    GroupOrderSessionRepository groupSessionRepository,
                                    GroupSessionParticipantRepository participantRepository) {
        this.orderRepository = orderRepository;
        this.groupSessionRepository = groupSessionRepository;
        this.participantRepository = participantRepository;
    }

    /** @param principal the signed-in user, or {@code null} for an anonymous connection */
    @Transactional(readOnly = true)
    public boolean canSubscribe(UserPrincipal principal, String destination) {
        if (destination == null) {
            return false;
        }
        if (PUBLIC_TABLE.matcher(destination).matches()
                || destination.equals("/topic/public.pickup-board")
                || destination.equals("/topic/tables.status")) {
            return true;
        }
        if (principal == null || principal.role() == null) {
            return false;
        }
        Role role = principal.role();
        switch (destination) {
            case "/topic/orders.new", "/topic/orders.status", "/topic/payments.status", "/topic/payments.failed",
                 "/topic/service-requests" -> {
                return FLOOR_STAFF.contains(role);
            }
            case "/topic/deliveries" -> {
                return FLOOR_STAFF.contains(role) || role == Role.RIDER;
            }
            case "/topic/inventory.alerts", "/topic/feedback.alerts" -> {
                return MANAGERS.contains(role);
            }
            default -> {
                // per order or per group below
            }
        }
        Matcher order = ORDER.matcher(destination);
        if (order.matches()) {
            if (FLOOR_STAFF.contains(role)) {
                return true;
            }
            OrderEntity entity = orderRepository.findById(UUID.fromString(order.group(1))).orElse(null);
            if (entity == null) {
                return false;
            }
            return principal.userId().equals(entity.getCustomerUserId())
                    || (role == Role.RIDER && principal.userId().equals(entity.getRiderUserId()));
        }
        Matcher group = GROUP.matcher(destination);
        if (group.matches()) {
            if (FLOOR_STAFF.contains(role)) {
                return true;
            }
            return groupSessionRepository.findBySessionCodeIgnoreCase(group.group(1))
                    .map(session -> session.getHostUserId().equals(principal.userId())
                            || participantRepository.findBySessionIdAndUserId(session.getId(), principal.userId()).isPresent())
                    .orElse(false);
        }
        return false;
    }
}
