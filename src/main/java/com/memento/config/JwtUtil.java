package com.memento.config;

import com.memento.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    // Cached once at startup — decoding Base64 + deriving the key is cryptographic
    // work that never changes, so there's no reason to repeat it on every request.
    private SecretKey signingKey;

    // @PostConstruct runs once after Spring injects @Value fields.
    // Safe to cache here because 'secret' is immutable for the lifetime of the app.
    @PostConstruct
    private void init() {
        signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    // Generate a signed JWT containing userId, username and role as claims.
    // The token is self-contained — the server doesn't store it anywhere.
    public String generate(Long userId, String username, Role role) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    // Parse and verify the token's signature, then return its claims (payload).
    // Throws JwtException if the token is tampered with, malformed, or expired.
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Quick validity check used by JwtAuthFilter — returns false instead of throwing.
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

}
