package com.restaurantmanager.core.config;

import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.security.JwtService;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.security.WebSocketTopicAuthorizer;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

/**
 * Signs in STOMP connections and checks every subscription.
 *
 * <p>CONNECT may carry {@code Authorization: Bearer <access token>}; a valid token makes the connection that user's,
 * an invalid or expired one is refused, and no token gives an anonymous connection that can only use public topics.
 * Each SUBSCRIBE is checked with {@link WebSocketTopicAuthorizer}. Clients cannot SEND: the server only pushes.</p>
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtService jwtService;
    private final WebSocketTopicAuthorizer authorizer;

    public WebSocketAuthInterceptor(JwtService jwtService, WebSocketTopicAuthorizer authorizer) {
        this.jwtService = jwtService;
        this.authorizer = authorizer;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            String token = bearer(accessor.getFirstNativeHeader("Authorization"));
            if (token != null) {
                accessor.setUser(authenticate(token));
            }
        } else if (command == StompCommand.SUBSCRIBE) {
            String destination = accessor.getDestination();
            UserPrincipal principal = principal(accessor.getUser());
            if (!authorizer.canSubscribe(principal, destination)) {
                log.debug("Refused STOMP subscription to {} for {}", destination, principal == null ? "anonymous" : principal.userId());
                throw new MessageDeliveryException("Not allowed to subscribe to " + destination);
            }
        } else if (command == StompCommand.SEND) {
            throw new MessageDeliveryException("Sending messages is not supported");
        }
        return message;
    }

    private UsernamePasswordAuthenticationToken authenticate(String token) {
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (Exception e) {
            throw new MessageDeliveryException("Invalid or expired token");
        }
        if (jwtService.tokenType(claims) != com.restaurantmanager.core.common.TokenType.ACCESS) {
            throw new MessageDeliveryException("Invalid or expired token");
        }
        Role role = jwtService.role(claims);
        UserPrincipal principal = new UserPrincipal(jwtService.userId(claims), role, jwtService.branchId(claims));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private static UserPrincipal principal(Principal user) {
        if (user instanceof UsernamePasswordAuthenticationToken auth && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    private static String bearer(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String value = header.trim();
        return value.regionMatches(true, 0, "Bearer ", 0, 7) ? value.substring(7).trim() : value;
    }
}
