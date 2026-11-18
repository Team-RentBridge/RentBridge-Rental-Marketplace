package com.rentbridge.controller;

import com.rentbridge.dto.AddEquipmentRequest;
import com.rentbridge.dto.CreateEquipmentBlockRequest;
import com.rentbridge.model.UserContext;
import com.rentbridge.service.EquipmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    @Autowired
    private EquipmentService equipmentService;

    @GetMapping
    public ResponseEntity<?> getAllEquipment(
            @RequestParam(required = false) String price,
            @RequestParam(required = false) String featured) {
        try {
            List<Map<String, Object>> equipment = equipmentService.getAllEquipment(price, featured);
            return ResponseEntity.ok(equipment);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getEquipmentById(@PathVariable Long id) {
        try {
            Map<String, Object> equipment = equipmentService.getEquipmentById(id);
            return ResponseEntity.ok(equipment);
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<?> getAvailability(@PathVariable Long id,
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        try {
            return ResponseEntity.ok(equipmentService.getAvailability(id, from, to));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/availability-blocks")
    public ResponseEntity<?> addAvailabilityBlock(@PathVariable Long id,
            @RequestBody CreateEquipmentBlockRequest request, HttpServletRequest httpRequest) {
        UserContext userContext = (UserContext) httpRequest.getAttribute("userContext");
        if (userContext == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        try {
            return ResponseEntity.ok(
                equipmentService.addAvailabilityBlock(id, request, userContext.getUserId()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/availability-blocks/{blockId}")
    public ResponseEntity<?> removeAvailabilityBlock(@PathVariable Long id, @PathVariable Long blockId,
            HttpServletRequest httpRequest) {
        UserContext userContext = (UserContext) httpRequest.getAttribute("userContext");
        if (userContext == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        try {
            equipmentService.removeAvailabilityBlock(id, blockId, userContext.getUserId());
            return ResponseEntity.ok(Map.of("message", "Blocked dates removed"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/add")
    public ResponseEntity<?> addEquipment(@RequestBody AddEquipmentRequest request,
                                           HttpServletRequest httpRequest) {
        UserContext userContext = (UserContext) httpRequest.getAttribute("userContext");
        if (userContext == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        try {
            Map<String, Object> equipment = equipmentService.addEquipment(request, userContext.getUserId());
            return ResponseEntity.ok(equipment);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEquipment(@PathVariable Long id,
                                              HttpServletRequest httpRequest) {
        UserContext userContext = (UserContext) httpRequest.getAttribute("userContext");
        if (userContext == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        try {
            equipmentService.deleteEquipment(id, userContext.getUserId());
            return ResponseEntity.ok(Map.of("message", "Equipment deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
