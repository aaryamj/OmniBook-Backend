package com.backend.controller;

import com.backend.dto.InviteProviderRequest;
import com.backend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getDashboardStats(Principal principal) {
        try {
            return ResponseEntity.ok(adminService.getDashboardStats(principal.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/providers/invite")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> inviteProvider(@RequestBody InviteProviderRequest request, Principal principal) {
        try {
            adminService.inviteProvider(request, principal.getName());
            return ResponseEntity.ok().body("{\"success\":true, \"message\":\"Provider invited successfully\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false, \"message\":\"" + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/providers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllProviders(Principal principal) {
        try {
            return ResponseEntity.ok(adminService.getAllProviders(principal.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false, \"message\":\"" + e.getMessage() + "\"}");
        }
    }

    @PutMapping("/providers/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> approveProvider(@PathVariable Long id, Principal principal) {
        try {
            adminService.approveProvider(id, principal.getName());
            return ResponseEntity.ok().body("{\"success\":true, \"message\":\"Provider approved successfully\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false, \"message\":\"" + e.getMessage() + "\"}");
        }
    }

    @PutMapping("/providers/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> rejectProvider(@PathVariable Long id, Principal principal) {
        try {
            adminService.rejectProvider(id, principal.getName());
            return ResponseEntity.ok().body("{\"success\":true, \"message\":\"Provider rejected successfully\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false, \"message\":\"" + e.getMessage() + "\"}");
        }
    }

    @PutMapping("/providers/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> suspendProvider(@PathVariable Long id, Principal principal) {
        try {
            adminService.suspendProvider(id, principal.getName());
            return ResponseEntity.ok().body("{\"success\":true, \"message\":\"Provider access suspended successfully\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false, \"message\":\"" + e.getMessage() + "\"}");
        }
    }

    @PutMapping("/providers/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> reactivateProvider(@PathVariable Long id, Principal principal) {
        try {
            adminService.reactivateProvider(id, principal.getName());
            return ResponseEntity.ok().body("{\"success\":true, \"message\":\"Provider access reactivated successfully\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false, \"message\":\"" + e.getMessage() + "\"}");
        }
    }

    @PutMapping("/appointments/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateAppointmentStatus(@PathVariable Long id, @RequestParam String status, Principal principal) {
        try {
            adminService.updateAppointmentStatus(id, status, principal.getName());
            return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Status updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/appointments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> cancelAppointment(@PathVariable Long id, Principal principal) {
        try {
            adminService.cancelAppointment(id, principal.getName());
            return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Appointment cancelled successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/appointments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllAppointments(Principal principal) {
        try {
            return ResponseEntity.ok(adminService.getAllAppointments(principal.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    @GetMapping("/patients")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllPatients(Principal principal) {
        try {
            return ResponseEntity.ok(adminService.getAllPatients(principal.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/appointments/{id}/reschedule")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> rescheduleAppointment(@PathVariable Long id, @RequestParam String newDate, @RequestParam String newTime, Principal principal) {
        try {
            adminService.rescheduleAppointment(id, newDate, newTime, principal.getName());
            return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Appointment rescheduled successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/appointments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createWalkInAppointment(@RequestBody com.backend.dto.WalkInAppointmentRequest request, Principal principal) {
        try {
            adminService.createWalkInAppointment(request, principal.getName());
            return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Walk-in appointment created successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/ledger/reconciliation")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getLedgerReconciliation(Principal principal) {
        try {
            return ResponseEntity.ok(adminService.getLedgerReconciliation(principal.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/ledger/settle")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> runDailySettlement(Principal principal) {
        try {
            adminService.runDailySettlement(principal.getName());
            return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Daily settlement executed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }
}
