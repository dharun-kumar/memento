package com.memento.config;

import com.memento.filter.JwtAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    // Static assets (WebJars, Swagger UI, API docs) are excluded from the security
    // filter chain entirely. This is more reliable than permitAll() inside a chain
    // because Spring Security 7.x uses MVC-based requestMatchers by default — paths
    // that have no @Controller mapping (like /webjars/**) can fail to match, causing
    // unauthenticated requests to be redirected to /login even when permitAll is set.
    // web.ignoring() bypasses the chain before any matcher runs, so it always works.
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring()
                .requestMatchers("/webjars/**")          // Bootstrap CSS/JS from JAR
                .requestMatchers("/swagger-ui/**")       // Swagger UI static files
                .requestMatchers("/v3/api-docs/**");     // OpenAPI JSON spec
    }

    // JwtAuthFilter is a @Component so Spring can inject it via constructor above.
    // But @Component also causes Spring Boot to auto-register it as a top-level servlet
    // filter, which would run it on every path (including /login, /actuator).
    // Setting enabled=false here prevents that auto-registration.
    // The filter is still used — it's added manually inside jwtFilterChain below.
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    // ── Filter Chain 1: JWT / API ─────────────────────────────────────────────
    // Handles only /api/** requests. Must be @Order(1) so it is evaluated before
    // the catch-all session chain below.
    //
    // Two types of clients can access /api/**:
    //   • AI agents  → send "Authorization: Bearer <token>" header (JWT auth)
    //   • Browser    → already have a session from /login (session auth)
    // SessionCreationPolicy.NEVER means this chain reuses an existing session but
    // never starts a new one, keeping agents completely stateless.
    @Bean
    @Order(1)
    public SecurityFilterChain jwtFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")

            // CSRF protection is for browser form submissions. Agents use JWT (stateless)
            // and browser users only read data via API, so CSRF is not needed here.
            .csrf(csrf -> csrf.disable())

            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.NEVER))

            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()
            )

            // When an unauthenticated browser user hits an API URL directly,
            // redirect them to /login instead of returning a raw 401 JSON error.
            // Agents always attach a valid JWT, so they never reach this entry point.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                        response.sendRedirect("/login"))
            )

            // Run our JWT filter before Spring Security's default username/password filter.
            // If a valid Bearer token is found, it sets the user in the SecurityContext
            // and the request proceeds as authenticated.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ── Filter Chain 2: Session / Browser (catch-all) ─────────────────────────
    // No securityMatcher — this chain handles every request not claimed by Chain 1.
    // Any unauthenticated request is automatically redirected to /login by formLogin.
    @Bean
    @Order(2)
    public SecurityFilterChain sessionFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF is off because logout uses a GET link (not a form POST).
            // This chain is admin/browser only, so the risk is acceptable.
            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll() // health check — open for monitoring
                .requestMatchers("/login").permitAll()       // login page — must be public
                .anyRequest().authenticated()               // everything else needs login
            )

            // formLogin sets up two things:
            //   1. Redirects unauthenticated users to /login (our LoginController)
            //   2. Handles POST /login automatically (validates credentials via CustomUserDetailsService)
            // loginPage("/login") tells Spring Security we own the login page — disables
            // the auto-generated DefaultLoginPageGeneratingFilter.
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/auth/token", true)
                .permitAll()
            )

            // Logout is triggered by a GET link so no CSRF token is needed in the HTML.
            // logoutRequestMatcher accepts a lambda instead of the removed AntPathRequestMatcher.
            // Spring invalidates the session, then redirects to /login?logout.
            .logout(logout -> logout
                .logoutRequestMatcher(request ->
                        "GET".equals(request.getMethod()) && "/logout".equals(request.getRequestURI()))
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );

        return http.build();
    }

    // BCrypt is the industry standard for password hashing.
    // Cost factor defaults to 10 — slow enough to resist brute-force, fast enough for login.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
