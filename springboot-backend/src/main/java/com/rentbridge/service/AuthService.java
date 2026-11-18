package com.rentbridge.service;

import com.rentbridge.dto.LoginRequest;
import com.rentbridge.dto.SignupRequest;
import com.rentbridge.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    public Map<String, Object> register(SignupRequest request) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE email = ?",
                    Integer.class, request.getEmail());
            if (count != null && count > 0) {
                throw new RuntimeException("Email already registered");
            }
        } catch (EmptyResultDataAccessException ignored) {}

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        Map<String, Object> newUser = jdbcTemplate.queryForMap(
                "INSERT INTO users (name, email, password, role) VALUES (?, ?, ?, 'user') RETURNING id, name, email, role",
                request.getName(), request.getEmail(), hashedPassword);

        Long userId = ((Number) newUser.get("id")).longValue();
        String token = jwtUtil.generateUserToken(userId, (String) newUser.get("name"), (String) newUser.get("email"));

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", userId);
        result.put("name", newUser.get("name"));
        result.put("email", newUser.get("email"));
        result.put("role", newUser.get("role"));
        result.put("isAdmin", false);
        return result;
    }

    public Map<String, Object> login(LoginRequest request) {
        // Check admin table first (admin table has: id, email, password only)
        try {
            Map<String, Object> admin = jdbcTemplate.queryForMap(
                    "SELECT * FROM admin WHERE email = ?", request.getEmail());

            String storedPassword = (String) admin.get("password");
            boolean passwordMatch;
            if (storedPassword.startsWith("$2")) {
                passwordMatch = passwordEncoder.matches(request.getPassword(), storedPassword);
            } else {
                passwordMatch = storedPassword.equals(request.getPassword());
            }

            if (!passwordMatch) {
                throw new RuntimeException("Invalid credentials");
            }

            Long adminId = ((Number) admin.get("id")).longValue();
            String token = jwtUtil.generateAdminToken(adminId, "Admin", (String) admin.get("email"));

            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("adminId", adminId);
            result.put("name", "Admin");
            result.put("email", admin.get("email"));
            result.put("isAdmin", true);
            return result;

        } catch (EmptyResultDataAccessException ignored) {}

        // Check users table
        try {
            Map<String, Object> user = jdbcTemplate.queryForMap(
                    "SELECT * FROM users WHERE email = ?", request.getEmail());

            String storedPassword = (String) user.get("password");
            if (!passwordEncoder.matches(request.getPassword(), storedPassword)) {
                throw new RuntimeException("Invalid credentials");
            }

            Long userId = ((Number) user.get("id")).longValue();
            String name = (String) user.get("name");
            String email = (String) user.get("email");
            String token = jwtUtil.generateUserToken(userId, name, email);

            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("userId", userId);
            result.put("name", name);
            result.put("email", email);
            result.put("role", user.get("role"));
            result.put("isAdmin", false);
            return result;

        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Invalid credentials");
        }
    }
}
