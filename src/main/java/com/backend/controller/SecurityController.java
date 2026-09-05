package com.backend.controller;

import com.backend.dto.RoleRequest;
import com.backend.dto.RoleResponse;
import com.backend.model.AuditLog;
import com.backend.model.TenantRole;
import com.backend.model.User;
import com.backend.repository.TenantRoleRepository;
import com.backend.repository.UserRepository;
import com.backend.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import com.backend.dto.AuditLogResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SecurityController {

    private final UserRepository userRepository;
    private final TenantRoleRepository tenantRoleRepository;
    private final AuditLogService auditLogService;

    @GetMapping("/roles")
    public ResponseEntity<?> getRoles(Principal principal) {
        try {
            User admin = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            List<TenantRole> roles = tenantRoleRepository.findByTenantId(admin.getTenant().getId());
            List<User> users = userRepository.findByTenantId(admin.getTenant().getId());

            List<RoleResponse> roleResponses = roles.stream().map(role -> {
                long count = users.stream()
                        .filter(u -> u.getTenantRole() != null && u.getTenantRole().getId().equals(role.getId()))
                        .count();
                return RoleResponse.builder()
                        .id(role.getId())
                        .roleName(role.getRoleName())
                        .accessScope(role.getAccessScope())
                        .privilegeLevel(role.getPrivilegeLevel())
                        .assignedUsers(count)
                        .build();
            }).collect(Collectors.toList());

            return ResponseEntity.ok(roleResponses);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error fetching roles: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/roles")
    public ResponseEntity<?> createRole(@RequestBody RoleRequest request, Principal principal) {
        try {
            User admin = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            TenantRole role = TenantRole.builder()
                    .tenant(admin.getTenant())
                    .roleName(request.getRoleName())
                    .accessScope(request.getAccessScope())
                    .privilegeLevel(request.getPrivilegeLevel())
                    .build();

            tenantRoleRepository.save(role);

            auditLogService.logAction(admin, "Created Custom Role: " + role.getRoleName(), "127.0.0.1");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Role created successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error creating role: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/roles/{id}")
    public ResponseEntity<?> updateRole(@PathVariable Long id, @RequestBody RoleRequest request, Principal principal) {
        try {
            User admin = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            TenantRole role = tenantRoleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Role not found"));

            if (!role.getTenant().getId().equals(admin.getTenant().getId())) {
                throw new RuntimeException("Unauthorized to update this role");
            }

            role.setRoleName(request.getRoleName());
            role.setAccessScope(request.getAccessScope());
            role.setPrivilegeLevel(request.getPrivilegeLevel());

            tenantRoleRepository.save(role);

            auditLogService.logAction(admin, "Updated Custom Role: " + role.getRoleName(), "127.0.0.1");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Role updated successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error updating role: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<?> getAuditLogs(Principal principal) {
        try {
            User admin = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
                    
            List<AuditLog> logs = auditLogService.getRecentLogs(admin.getTenant().getId());
            
            List<AuditLogResponse> responseLogs = logs.stream().map(log -> AuditLogResponse.builder()
                    .id(log.getId())
                    .eventAction(log.getEventAction())
                    .sourceIp(log.getSourceIp())
                    .timestamp(log.getTimestamp())
                    .user(AuditLogResponse.UserDto.builder()
                            .id(log.getUser().getId())
                            .fullName(log.getUser().getFullName())
                            .email(log.getUser().getEmail())
                            .build())
                    .build()
            ).collect(Collectors.toList());
            
            return ResponseEntity.ok(responseLogs);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error fetching audit logs: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
