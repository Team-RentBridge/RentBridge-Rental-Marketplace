package com.rentbridge.security;

import com.rentbridge.model.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Read raw token from Authorization header (no "Bearer " prefix)
        String token = request.getHeader("Authorization");

        if (StringUtils.hasText(token) && !token.startsWith("Bearer ")) {
            // Raw token (original Node.js style)
            processToken(token, request);
        } else if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            // Also support Bearer scheme for compatibility
            processToken(token.substring(7), request);
        }

        filterChain.doFilter(request, response);
    }

    private void processToken(String token, HttpServletRequest request) {
        try {
            Claims claims = jwtUtil.validateToken(token);
            boolean isAdmin = jwtUtil.isAdmin(claims);

            UserContext userContext;
            if (isAdmin) {
                Long adminId = jwtUtil.getAdminId(claims);
                String name = jwtUtil.getName(claims);
                String email = jwtUtil.getEmail(claims);
                userContext = new UserContext(adminId, name, email, "admin", true);
            } else {
                Long userId = jwtUtil.getUserId(claims);
                String name = jwtUtil.getName(claims);
                String email = jwtUtil.getEmail(claims);
                userContext = new UserContext(userId, name, email, "user", false);
            }

            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + userContext.getRole().toUpperCase()));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userContext, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setAttribute("userContext", userContext);

        } catch (Exception e) {
            // Invalid token - just proceed without setting auth
            logger.debug("JWT validation failed: " + e.getMessage());
        }
    }
}
