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
    private final PublicBookingController publicBookingController;
    private final com.backend.service.DailySettlementService dailySettlementService;
    private final com.backend.repository.UserRepository userRepository;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getDashboardStats(
            @RequestParam(required = false, defaultValue = "All Time") String timeFilter,
            Principal principal) {
        try {
            return ResponseEntity.ok(adminService.getDashboardStats(principal.getName(), timeFilter));
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
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_PROVIDER', 'PROVIDER')")
    public ResponseEntity<?> getAllProviders(Principal principal) {
        try {
            return ResponseEntity.ok(adminService.getAllProviders(principal.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\":false, \"message\":\"" + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/providers/{id}/services")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_PROVIDER', 'PROVIDER')")
    public ResponseEntity<?> getProviderServices(@PathVariable Long id) {
        return publicBookingController.getServicesByProvider(id);
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

    @PutMapping("/providers/{id}/commission")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateProviderCommission(@PathVariable Long id, @RequestBody java.util.Map<String, Object> request, Principal principal) {
        try {
            Double commissionRate = null;
            if (request.containsKey("commissionRate") && request.get("commissionRate") != null) {
                commissionRate = Double.valueOf(request.get("commissionRate").toString());
            }
            adminService.updateProviderCommission(id, commissionRate, principal.getName());
            return ResponseEntity.ok(java.util.Map.of(
                "success", true, 
                "message", "Provider commission rate updated successfully",
                "commissionRate", commissionRate != null ? commissionRate : "DEFAULT"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
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

    @PutMapping("/appointments/{id}/payment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> recordAppointmentPayment(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> body,
            Principal principal) {
        try {
            String paymentMethod = body.containsKey("paymentMethod") && body.get("paymentMethod") != null 
                    ? body.get("paymentMethod").toString() : "CASH";
            String paymentStatus = body.containsKey("paymentStatus") && body.get("paymentStatus") != null 
                    ? body.get("paymentStatus").toString() : "SUCCESS";
            Double amount = null;
            if (body.containsKey("amount") && body.get("amount") != null) {
                amount = Double.valueOf(body.get("amount").toString());
            }

            adminService.recordAppointmentPayment(id, paymentMethod, paymentStatus, amount, principal.getName());
            return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Payment recorded successfully"));
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

    @GetMapping("/appointments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAppointmentDetails(@PathVariable Long id, Principal principal) {
        try {
            return ResponseEntity.ok(adminService.getAppointmentDetailsById(id, principal.getName()));
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
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_PROVIDER', 'PROVIDER')")
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

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_PROVIDER', 'PROVIDER')")
    public ResponseEntity<?> globalSearch(@RequestParam(name = "q", defaultValue = "") String query, Principal principal) {
        try {
            return ResponseEntity.ok(adminService.globalSearch(principal.getName(), query));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ==========================================
    // DAILY SETTLEMENT HUB (ORG ADMIN)
    // ==========================================

    @GetMapping("/settlements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getTenantSettlements(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            @RequestParam(required = false) String status,
            Principal principal) {
        try {
            com.backend.model.User admin = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            return ResponseEntity.ok(dailySettlementService.getAdminOverview(admin.getTenant().getId(), startDate, endDate, status));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/settlements/{date}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getTenantSettlementDetail(
            @PathVariable @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            Principal principal) {
        try {
            com.backend.model.User admin = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            return ResponseEntity.ok(dailySettlementService.getDailySettlementDetail(admin.getTenant().getId(), date));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/settlements/{date}/finalize")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> finalizeSettlement(
            @PathVariable @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            Principal principal) {
        try {
            com.backend.model.User admin = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            return ResponseEntity.ok(dailySettlementService.finalizeDailySettlement(admin.getTenant().getId(), date, principal.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }
}
