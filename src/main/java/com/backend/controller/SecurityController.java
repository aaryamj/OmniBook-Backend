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
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.backend.repository.ProviderProfileRepository providerProfileRepository;

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
                        .permissionsJson(role.getPermissionsJson())
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
                    .permissionsJson(request.getPermissionsJson())
                    .build();

            tenantRoleRepository.save(role);

            auditLogService.logAction(admin, "Created Custom Role: " + role.getRoleName(), "127.0.0.1");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Role created successfully");
            response.put("role", role);
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
            role.setPermissionsJson(request.getPermissionsJson());

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

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<?> deleteRole(@PathVariable Long id, Principal principal) {
        try {
            User admin = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            TenantRole role = tenantRoleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Role not found"));

            if (!role.getTenant().getId().equals(admin.getTenant().getId())) {
                throw new RuntimeException("Unauthorized to delete this role");
            }

            // Unlink any users assigned to this role before deleting
            List<User> assignedUsers = userRepository.findByTenantId(admin.getTenant().getId()).stream()
                    .filter(u -> u.getTenantRole() != null && u.getTenantRole().getId().equals(role.getId()))
                    .collect(Collectors.toList());

            for (User u : assignedUsers) {
                u.setTenantRole(null);
                userRepository.save(u);
            }

            tenantRoleRepository.delete(role);
            auditLogService.logAction(admin, "Deleted Custom Role: " + role.getRoleName(), "127.0.0.1");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Role deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error deleting role: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/roles/{id}/users")
    public ResponseEntity<?> getRoleUsers(@PathVariable Long id, Principal principal) {
        try {
            User admin = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            TenantRole role = tenantRoleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Role not found"));

            if (!role.getTenant().getId().equals(admin.getTenant().getId())) {
                throw new RuntimeException("Unauthorized to access this role's users");
            }

            List<User> tenantUsers = userRepository.findByTenantId(admin.getTenant().getId());
            List<Map<String, Object>> result = tenantUsers.stream().map(u -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", u.getId());
                map.put("fullName", u.getFullName() != null ? u.getFullName() : "Unknown");
                map.put("email", u.getEmail());
                map.put("role", u.getRole());
                map.put("isAssigned", u.getTenantRole() != null && u.getTenantRole().getId().equals(role.getId()));
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error fetching users for role: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/roles/{id}/assign")
    public ResponseEntity<?> assignRoleUsers(@PathVariable Long id, @RequestBody Map<String, List<Long>> payload, Principal principal) {
        try {
            User admin = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            TenantRole role = tenantRoleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Role not found"));

            if (!role.getTenant().getId().equals(admin.getTenant().getId())) {
                throw new RuntimeException("Unauthorized to modify this role");
            }

            List<Long> targetUserIds = payload.getOrDefault("userIds", List.of());
            List<User> tenantUsers = userRepository.findByTenantId(admin.getTenant().getId());

            for (User u : tenantUsers) {
                if (targetUserIds.contains(u.getId())) {
                    u.setTenantRole(role);
                    userRepository.save(u);
                } else if (u.getTenantRole() != null && u.getTenantRole().getId().equals(role.getId())) {
                    u.setTenantRole(null);
                    userRepository.save(u);
                }
            }

            auditLogService.logAction(admin, "Updated assigned users for role: " + role.getRoleName(), "127.0.0.1");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User assignments updated successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error updating role assignments: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/roles/{id}/create-user")
    public ResponseEntity<?> createAndAssignUser(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody com.backend.dto.CreateStaffUserRequestDTO request,
            Principal principal) {
        try {
            User admin = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            TenantRole role = tenantRoleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Role not found"));

            if (!role.getTenant().getId().equals(admin.getTenant().getId())) {
                throw new RuntimeException("Unauthorized to assign users to this role");
            }

            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "A user with this email already exists");
                return ResponseEntity.badRequest().body(err);
            }

            if (userRepository.findByPhone(request.getPhone()).isPresent()) {
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "A user with this mobile number already exists");
                return ResponseEntity.badRequest().body(err);
            }

            User newUser = User.builder()
                    .fullName(request.getFullName())
                    .email(request.getEmail())
                    .phone(request.getPhone())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role("service_provider")
                    .enabled(true)
                    .tenant(admin.getTenant())
                    .tenantRole(role)
                    .authProvider(com.backend.model.AuthProvider.LOCAL)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();

            User savedUser = userRepository.save(newUser);

            // Initialize provider profile for this staff user
            com.backend.model.ProviderProfile profile = new com.backend.model.ProviderProfile();
            profile.setUser(savedUser);
            profile.setStatus(com.backend.model.ProviderStatus.ACTIVE);
            profile.setPrimarySpecialty(role.getRoleName());
            if (admin.getTenant() != null) {
                profile.setTier(admin.getTenant().getSubscriptionTier());
            }
            providerProfileRepository.save(profile);

            auditLogService.logAction(admin, "Provisioned new staff user " + savedUser.getEmail() + " assigned to role: " + role.getRoleName(), "127.0.0.1");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User provisioned and assigned successfully");
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", savedUser.getId());
            userMap.put("fullName", savedUser.getFullName());
            userMap.put("email", savedUser.getEmail());
            userMap.put("phone", savedUser.getPhone());
            userMap.put("role", savedUser.getRole());
            userMap.put("roleName", role.getRoleName());
            userMap.put("isAssigned", true);
            response.put("user", userMap);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error creating and assigning user: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
