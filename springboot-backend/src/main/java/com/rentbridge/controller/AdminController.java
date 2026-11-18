package com.rentbridge.controller;

import com.rentbridge.model.UserContext;
import com.rentbridge.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    private boolean isAdmin(HttpServletRequest httpRequest) {
        UserContext userContext = (UserContext) httpRequest.getAttribute("userContext");
        return userContext != null && Boolean.TRUE.equals(userContext.getIsAdmin());
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getAdminStats(HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        try {
            Map<String, Object> stats = adminService.getAdminStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        try {
            List<Map<String, Object>> users = adminService.getAllUsers();
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/bookings")
    public ResponseEntity<?> getAllBookings(HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        try {
            List<Map<String, Object>> bookings = adminService.getAllBookings();
            return ResponseEntity.ok(bookings);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id,
                                         HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        try {
            adminService.deleteUser(id);
            return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
