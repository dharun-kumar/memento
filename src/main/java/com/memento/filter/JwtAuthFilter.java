package com.memento.filter;

import com.memento.config.JwtUtil;
import com.memento.service.CustomUserDetailsService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Reads the JWT from the Authorization header on every /api/** request.
// If the token is valid, sets the user in SecurityContext so Spring Security
// treats the request as authenticated for the rest of the lifecycle.
//
// OncePerRequestFilter guarantees this logic runs exactly once per request,
// even if the filter is referenced in multiple places in the chain.
//
// @Component lets Spring inject this into SecurityConfig.
// FilterRegistrationBean in SecurityConfig prevents Spring Boot from also
// auto-registering it as a global servlet filter (which would run it on /login etc.).
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthFilter(JwtUtil jwtUtil, CustomUserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        // No Bearer token present — skip JWT logic and let Spring Security decide
        // whether the request is allowed (it will reject if the endpoint requires auth)
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7); // strip "Bearer " prefix

        if (jwtUtil.isValid(token)) {
            Claims claims = jwtUtil.parse(token);
            String username = claims.getSubject();

            // Load full user details from DB (needed for authorities/roles)
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // Build an authenticated token and store it in the SecurityContext.
            // From this point the request is treated as authenticated — controllers
            // can call SecurityContextHolder.getContext().getAuthentication() to get the user.
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

}
