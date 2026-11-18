package com.rentbridge.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class RootController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/")
    public ResponseEntity<String> root() {
        return ResponseEntity.ok("Backend Running 🚀");
    }

    @GetMapping("/test-db")
    public ResponseEntity<?> testDb() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("message", "Database connection successful ✅"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Database connection failed: " + e.getMessage()));
        }
    }

    @GetMapping("/schema")
    public ResponseEntity<?> getSchema() {
        try {
            List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'public' ORDER BY table_name");
            return ResponseEntity.ok(tables);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/schema/all")
    public ResponseEntity<?> getAllColumns() {
        try {
            List<Map<String, Object>> cols = jdbcTemplate.queryForList(
                "SELECT table_name, column_name, data_type, is_nullable, column_default " +
                "FROM information_schema.columns " +
                "WHERE table_schema = 'public' " +
                "ORDER BY table_name, ordinal_position");
            return ResponseEntity.ok(cols);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
