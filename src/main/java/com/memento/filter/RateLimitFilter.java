package com.memento.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Global rate limiter applied to every incoming request before Spring Security runs.
// Uses the token-bucket algorithm: each IP gets a bucket of tokens.
// Each request costs one token. Tokens refill at the configured rate.
// When the bucket is empty the request is rejected with HTTP 429.
//
// @Component auto-registers this as a top-level servlet filter — intentional,
// since we want rate limiting to apply to every path including /login.
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // Configured in application.properties — no hardcoded values here
    @Value("${app.rate-limit.capacity}")
    private int capacity;

    @Value("${app.rate-limit.refill-per-minute}")
    private int refillPerMinute;

    // One bucket per IP address — created lazily on the first request from that IP.
    // ConcurrentHashMap is thread-safe for concurrent requests from different IPs.
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket newBucket() {
        // Greedy refill: tokens are added continuously over the minute (not all at once
        // at the end), which smooths out the refill and prevents burst abuse at boundaries.
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String ip = request.getRemoteAddr();
        // computeIfAbsent atomically creates a bucket for new IPs — safe under concurrent load
        Bucket bucket = buckets.computeIfAbsent(ip, k -> newBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response); // token available — let the request through
        } else {
            // Return 429 Too Many Requests with a plain JSON body
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"error\": \"Too many requests. Limit is " + refillPerMinute + " requests per minute.\"}");
        }
    }

}
