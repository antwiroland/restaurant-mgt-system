package com.restaurantmanager.core.security;

import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.config.RateLimitProps;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(RateLimitProps.class)
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final String[] allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          RateLimitFilter rateLimitFilter,
                          @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:19006}") String allowedOrigins) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/auth/register", "/auth/login", "/auth/refresh", "/actuator/health").permitAll()
                        .requestMatchers("/auth/otp/request", "/auth/otp/verify", "/auth/otp/login").permitAll()
                        .requestMatchers("/auth/password/forgot", "/auth/password/reset", "/auth/email/verify").permitAll()
                        .requestMatchers("/actuator/prometheus", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/menu/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/delivery/zones").permitAll()
                        .requestMatchers(HttpMethod.GET, "/billing/settings").permitAll()
                        .requestMatchers(HttpMethod.GET, "/restaurant/profile").permitAll()
                        .requestMatchers(HttpMethod.GET, "/careers", "/careers/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/media/**").permitAll()
                        .requestMatchers("/tables/scan").permitAll()
                        .requestMatchers(HttpMethod.POST, "/qr/sessions/link").permitAll()
                        .requestMatchers(HttpMethod.GET, "/tables/public").permitAll()
                        .requestMatchers("/tables/public/*/requests").permitAll()
                        .requestMatchers("/orders/public/**").permitAll()
                        .requestMatchers("/reservations").permitAll()
                        .requestMatchers("/reservations/guest/**").permitAll()
                        .requestMatchers("/reservations/*/guest-cancel").permitAll()
                        .requestMatchers("/payments/webhook").permitAll()
                        .requestMatchers("/ws/**", "/ws").permitAll()
                        // What an HR-role login may call: the HR module, its own account and the lookups HR screens use.
                        .requestMatchers("/hr/**", "/auth/**", "/users/me", "/notifications/devices", "/notifications/devices/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/branches", "/scheduling/staff").authenticated()
                        // Everything else (POS, orders, payments, menu admin, reports...) is refused to the HR role
                        .anyRequest().access(SecurityConfig::authenticatedOutsideHrRole)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) -> response.sendError(HttpServletResponse.SC_FORBIDDEN))
                )
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Signed in, and not an HR-role account (those work only in the HR module). */
    static AuthorizationDecision authenticatedOutsideHrRole(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        boolean signedIn = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        boolean hrRole = signedIn && auth.getAuthorities().stream().anyMatch(a -> HR_AUTHORITY.equals(a.getAuthority()));
        return new AuthorizationDecision(signedIn && !hrRole);
    }

    private static final String HR_AUTHORITY = "ROLE_" + Role.HR.name();

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Page", "X-Size", "X-Total-Elements", "X-Total-Pages"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
