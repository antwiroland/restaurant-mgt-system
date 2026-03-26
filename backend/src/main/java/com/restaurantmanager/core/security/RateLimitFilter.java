package com.restaurantmanager.core.security;

import com.restaurantmanager.core.config.RateLimitProps;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Pattern USER_PIN_PATH = Pattern.compile("^/users/[^/]+/pin$");

    private final RateLimitProps rateLimitProps;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong(0);

    public RateLimitFilter(RateLimitProps rateLimitProps) {
        this.rateLimitProps = rateLimitProps;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!rateLimitProps.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        LimitSpec spec = resolveSpec(request);
        if (spec == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = spec.bucket() + ":" + resolveClientIp(request);
        long now = System.currentTimeMillis();
        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(now));
        boolean allowed = counter.allow(now, spec.limit(), spec.windowMs());
        if (!allowed) {
            writeTooManyRequests(response);
            return;
        }

        maybeCleanup(now);
        filterChain.doFilter(request, response);
    }

    private LimitSpec resolveSpec(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"POST".equalsIgnoreCase(method)) {
            return null;
        }

        String path = request.getRequestURI();
        if ("/auth/login".equals(path) || "/auth/register".equals(path) || "/auth/refresh".equals(path)) {
            return new LimitSpec("auth", rateLimitProps.getAuthRequests(), rateLimitProps.getAuthWindowSeconds() * 1000L);
        }
        if ("/auth/pin/verify".equals(path) || USER_PIN_PATH.matcher(path).matches()) {
            return new LimitSpec("pin", rateLimitProps.getPinRequests(), rateLimitProps.getPinWindowSeconds() * 1000L);
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":429,\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Too many requests. Please try again later.\",\"timestamp\":\"" + Instant.now() + "\"}");
    }

    private void maybeCleanup(long nowMs) {
        long current = requestCount.incrementAndGet();
        if (current % 500 != 0) {
            return;
        }
        long maxWindowMs = Math.max(rateLimitProps.getAuthWindowSeconds(), rateLimitProps.getPinWindowSeconds()) * 1000L;
        counters.entrySet().removeIf(entry -> nowMs - entry.getValue().windowStartMs() > maxWindowMs * 2);
    }

    private record LimitSpec(String bucket, int limit, long windowMs) {
    }

    private static final class WindowCounter {
        private long windowStartMs;
        private int count;

        private WindowCounter(long nowMs) {
            this.windowStartMs = nowMs;
            this.count = 0;
        }

        private synchronized boolean allow(long nowMs, int limit, long windowMs) {
            if (nowMs - windowStartMs >= windowMs) {
                windowStartMs = nowMs;
                count = 0;
            }
            count++;
            return count <= limit;
        }

        private synchronized long windowStartMs() {
            return windowStartMs;
        }
    }
}
