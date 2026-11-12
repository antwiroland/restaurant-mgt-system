package com.restaurantmanager.core.security;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.config.WebSocketAuthInterceptor;
import com.restaurantmanager.core.order.GroupOrderSessionEntity;
import com.restaurantmanager.core.order.GroupSessionParticipantEntity;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.order.OrderType;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** STOMP connections are signed in with the access token, and every subscription is checked. */
class WebSocketAuthIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private WebSocketTopicAuthorizer authorizer;
    @Autowired
    private WebSocketAuthInterceptor interceptor;
    @Autowired
    @Qualifier("clientInboundChannel")
    private AbstractSubscribableChannel clientInboundChannel;

    private UserEntity cashier;
    private UserEntity manager;
    private UserEntity customer;
    private UserEntity otherCustomer;
    private UserEntity rider;
    private UserEntity hr;

    @BeforeEach
    void setUpData() {
        cashier = createUser("Kofi", "+233300000401", "k401@x.com", "secret123", Role.CASHIER);
        manager = createUser("Yaw", "+233300000402", "y402@x.com", "secret123", Role.MANAGER);
        customer = createUser("Ama", "+233300000403", "a403@x.com", "secret123", Role.CUSTOMER);
        otherCustomer = createUser("Esi", "+233300000404", "e404@x.com", "secret123", Role.CUSTOMER);
        rider = createUser("Kwame", "+233300000405", "r405@x.com", "secret123", Role.RIDER);
        hr = createUser("Efua", "+233300000406", "h406@x.com", "secret123", Role.HR);
    }

    @Test
    void interceptorIsAttachedToIncomingStompMessages() {
        assertThat(clientInboundChannel.getInterceptors()).contains(interceptor);
    }

    @Test
    void topicsFollowRoleAndOwnership() {
        OrderEntity order = order(customer.getId(), rider.getId());
        String orderTopic = "/topic/orders." + order.getId();

        // public: no sign-in needed
        assertThat(authorizer.canSubscribe(null, "/topic/public.tables.abcdef12-token.orders")).isTrue();
        assertThat(authorizer.canSubscribe(null, "/topic/public.tables.abcdef12-token.requests")).isTrue();
        assertThat(authorizer.canSubscribe(null, "/topic/public.pickup-board")).isTrue();
        assertThat(authorizer.canSubscribe(null, "/topic/tables.status")).isTrue();

        // staff topics: never anonymous, never customers or HR
        for (String staffTopic : new String[]{"/topic/orders.new", "/topic/orders.status", "/topic/payments.status", "/topic/service-requests"}) {
            assertThat(authorizer.canSubscribe(null, staffTopic)).isFalse();
            assertThat(authorizer.canSubscribe(p(customer), staffTopic)).isFalse();
            assertThat(authorizer.canSubscribe(p(hr), staffTopic)).isFalse();
            assertThat(authorizer.canSubscribe(p(cashier), staffTopic)).isTrue();
        }
        assertThat(authorizer.canSubscribe(p(cashier), "/topic/inventory.alerts")).isFalse();
        assertThat(authorizer.canSubscribe(p(manager), "/topic/inventory.alerts")).isTrue();
        assertThat(authorizer.canSubscribe(p(rider), "/topic/deliveries")).isTrue();
        assertThat(authorizer.canSubscribe(p(customer), "/topic/deliveries")).isFalse();

        // one order: its customer, its rider and floor staff
        assertThat(authorizer.canSubscribe(p(customer), orderTopic)).isTrue();
        assertThat(authorizer.canSubscribe(p(customer), orderTopic + ".payments")).isTrue();
        assertThat(authorizer.canSubscribe(p(rider), orderTopic + ".delivery")).isTrue();
        assertThat(authorizer.canSubscribe(p(cashier), orderTopic)).isTrue();
        assertThat(authorizer.canSubscribe(p(otherCustomer), orderTopic)).isFalse();
        assertThat(authorizer.canSubscribe(null, orderTopic)).isFalse();
        assertThat(authorizer.canSubscribe(p(customer), "/topic/orders." + UUID.randomUUID())).isFalse();

        // a group: its host and participants
        GroupOrderSessionEntity group = group(customer.getId(), otherCustomer.getId());
        assertThat(authorizer.canSubscribe(p(customer), "/topic/group." + group.getSessionCode())).isTrue();
        assertThat(authorizer.canSubscribe(p(otherCustomer), "/topic/group." + group.getSessionCode())).isTrue();
        assertThat(authorizer.canSubscribe(p(createUser("Nana", "+233300000407", "n407@x.com", "secret123", Role.CUSTOMER)),
                "/topic/group." + group.getSessionCode())).isFalse();

        // anything else is refused, even for an admin
        assertThat(authorizer.canSubscribe(p(manager), "/topic/anything")).isFalse();
        assertThat(authorizer.canSubscribe(p(manager), "/queue/x")).isFalse();
        assertThat(authorizer.canSubscribe(p(manager), "/topic/public.tables.x.orders")).isFalse(); // too short to be a token
    }

    @Test
    void connectSignsInWithTheAccessToken_andRefusesBadTokensAndSends() {
        Message<?> connected = interceptor.preSend(connect("Bearer " + accessToken(cashier)), null);
        Principal user = StompHeaderAccessor.wrap(connected).getUser();
        assertThat(user).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        UserPrincipal principal = (UserPrincipal) ((UsernamePasswordAuthenticationToken) user).getPrincipal();
        assertThat(principal.userId()).isEqualTo(cashier.getId());
        assertThat(principal.role()).isEqualTo(Role.CASHIER);

        // signed-in subscription allowed; anonymous refused
        interceptor.preSend(subscribe("/topic/orders.new", user), null);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/orders.new", null), null))
                .isInstanceOf(MessageDeliveryException.class);
        interceptor.preSend(subscribe("/topic/public.pickup-board", null), null);

        // no token: anonymous connection (public topics only)
        assertThat(StompHeaderAccessor.wrap(interceptor.preSend(connect(null), null)).getUser()).isNull();
        // bad, expired or refresh tokens are refused
        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer not-a-token"), null)).isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer " + expiredAccessToken(cashier)), null)).isInstanceOf(MessageDeliveryException.class);
        String refresh = jwtService.generateRefreshToken(cashier.getId(), cashier.getRole());
        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer " + refresh), null)).isInstanceOf(MessageDeliveryException.class);

        // clients only listen
        StompHeaderAccessor send = StompHeaderAccessor.create(StompCommand.SEND);
        send.setDestination("/app/anything");
        send.setLeaveMutable(true);
        assertThatThrownBy(() -> interceptor.preSend(MessageBuilder.createMessage(new byte[0], send.getMessageHeaders()), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    private static UserPrincipal p(UserEntity user) {
        return new UserPrincipal(user.getId(), user.getRole(), user.getBranch() == null ? null : user.getBranch().getId());
    }

    private static Message<byte[]> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<byte[]> subscribe(String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSubscriptionId("sub-1");
        if (user != null) {
            accessor.setUser(user);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private OrderEntity order(UUID customerId, UUID riderId) {
        OrderEntity order = new OrderEntity();
        order.setType(OrderType.DELIVERY);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setCustomerUserId(customerId);
        order.setRiderUserId(riderId);
        order.setDeliveryAddress("12 Oxford St");
        order.setSubtotal(new BigDecimal("40.00"));
        order.setTotal(new BigDecimal("40.00"));
        return orderRepository.save(order);
    }

    private GroupOrderSessionEntity group(UUID hostId, UUID participantId) {
        GroupOrderSessionEntity session = new GroupOrderSessionEntity();
        session.setSessionCode("GRP" + Instant.now().toEpochMilli() % 100000);
        session.setHostUserId(hostId);
        session = groupOrderSessionRepository.save(session);
        GroupSessionParticipantEntity participant = new GroupSessionParticipantEntity();
        participant.setSession(session);
        participant.setUserId(participantId);
        participant.setDisplayName("Esi");
        groupSessionParticipantRepository.save(participant);
        return session;
    }
}
