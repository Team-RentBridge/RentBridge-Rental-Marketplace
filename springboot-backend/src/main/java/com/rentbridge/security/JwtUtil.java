package com.rentbridge.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // Pad or truncate to 32 bytes for HMAC-SHA256
        byte[] paddedKey = new byte[32];
        System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
        return Keys.hmacShaKeyFor(paddedKey);
    }

    public String generateUserToken(Long userId, String name, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("name", name);
        claims.put("email", email);
        claims.put("isAdmin", false);
        return buildToken(claims, String.valueOf(userId));
    }

    public String generateAdminToken(Long adminId, String name, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("adminId", adminId);
        claims.put("name", name);
        claims.put("email", email);
        claims.put("isAdmin", true);
        return buildToken(claims, String.valueOf(adminId));
    }

    private String buildToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new RuntimeException("Token expired");
        } catch (SignatureException e) {
            throw new RuntimeException("Invalid token signature");
        } catch (MalformedJwtException e) {
            throw new RuntimeException("Malformed token");
        } catch (Exception e) {
            throw new RuntimeException("Invalid token");
        }
    }

    public boolean isAdmin(Claims claims) {
        Object isAdmin = claims.get("isAdmin");
        if (isAdmin instanceof Boolean) {
            return (Boolean) isAdmin;
        }
        return false;
    }

    public Long getUserId(Claims claims) {
        Object userId = claims.get("userId");
        if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        } else if (userId instanceof Long) {
            return (Long) userId;
        }
        return null;
    }

    public Long getAdminId(Claims claims) {
        Object adminId = claims.get("adminId");
        if (adminId instanceof Integer) {
            return ((Integer) adminId).longValue();
        } else if (adminId instanceof Long) {
            return (Long) adminId;
        }
        return null;
    }

    public String getName(Claims claims) {
        Object name = claims.get("name");
        return name != null ? name.toString() : null;
    }

    public String getEmail(Claims claims) {
        Object email = claims.get("email");
        return email != null ? email.toString() : null;
    }
}
