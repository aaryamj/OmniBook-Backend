package com.backend.controller;

import com.backend.dto.CancelRequestDTO;
import com.backend.dto.RescheduleRequestDTO;
import com.backend.dto.TenantPolicyDTO;
import com.backend.model.Appointment;
import com.backend.model.AppointmentLifecycleEvent;
import com.backend.model.User;
import com.backend.repository.UserRepository;
import com.backend.service.AppointmentLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AppointmentLifecycleController {

    private final AppointmentLifecycleService lifecycleService;
    private final UserRepository userRepository;
    private final com.backend.repository.TenantRepository tenantRepository;

    private User getCurrentAuthenticatedUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            String email = ((UserDetails) principal).getUsername();
            return userRepository.findByEmail(email).orElse(null);
        }
        return null;
    }

    /**
     * Preview cancellation refund eligibility and policy breakdown for user/patient.
     */
    @GetMapping("/user/appointments/{id}/cancel-preview")
    public ResponseEntity<?> previewCancellation(@PathVariable Long id) {
        try {
            User user = getCurrentAuthenticatedUser();
            String userEmail = user != null ? user.getEmail() : null;
            Map<String, Object> preview = lifecycleService.previewCancellation(id, userEmail);
            return ResponseEntity.ok(Map.of("success", true, "preview", preview));
        } catch (Exception e) {
            log.error("Error previewing cancellation for appointment {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Cancel an appointment (initiates refund calculation and releases slot).
     */
    @PostMapping("/user/appointments/{id}/cancel")
    public ResponseEntity<?> cancelAppointment(@PathVariable Long id, @Valid @RequestBody CancelRequestDTO request) {
        try {
            User user = getCurrentAuthenticatedUser();
            String userEmail = user != null ? user.getEmail() : null;
            String role = user != null ? user.getRole() : "patient";
            Appointment cancelled = lifecycleService.cancelAppointment(id, userEmail, request, role);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Appointment cancelled successfully.",
                    "refundStatus", cancelled.getRefundStatus() != null ? cancelled.getRefundStatus() : "NOT_ELIGIBLE",
                    "refundAmount", cancelled.getRefundAmount() != null ? cancelled.getRefundAmount() : 0.0,
                    "refundCurrency", cancelled.getRefundCurrency() != null ? cancelled.getRefundCurrency() : "NPR"
            ));
        } catch (Exception e) {
            log.error("Error cancelling appointment {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Reschedule an appointment to a new date and time slot.
     */
    @PostMapping("/user/appointments/{id}/reschedule")
    public ResponseEntity<?> rescheduleAppointment(@PathVariable Long id, @Valid @RequestBody RescheduleRequestDTO request) {
        try {
            User user = getCurrentAuthenticatedUser();
            String userEmail = user != null ? user.getEmail() : null;
            String role = user != null ? user.getRole() : "patient";
            Appointment rescheduled = lifecycleService.rescheduleAppointment(id, userEmail, request, role);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Appointment rescheduled successfully.",
                    "newDate", rescheduled.getAppointmentDate(),
                    "newTime", rescheduled.getAppointmentTime(),
                    "rescheduleCount", rescheduled.getRescheduleCount()
            ));
        } catch (Exception e) {
            log.error("Error rescheduling appointment {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Retrieve full chronological lifecycle history for an appointment.
     */
    @GetMapping("/user/appointments/{id}/lifecycle-events")
    public ResponseEntity<?> getLifecycleEvents(@PathVariable Long id) {
        try {
            List<AppointmentLifecycleEvent> events = lifecycleService.getLifecycleEvents(id);
            return ResponseEntity.ok(Map.of("success", true, "events", events));
        } catch (Exception e) {
            log.error("Error fetching lifecycle events for appointment {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Admin: Get cancellation and refund policy for tenant.
     */
    @GetMapping("/admin/appointment-policy")
    public ResponseEntity<?> getAdminTenantPolicy() {
        try {
            User user = getCurrentAuthenticatedUser();
            if (user == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "User not authenticated."));
            }
            Long tenantId = user.getTenant() != null ? user.getTenant().getId() : null;
            if (tenantId == null) {
                tenantId = tenantRepository.findAll().stream().findFirst().map(com.backend.model.Tenant::getId).orElse(null);
            }
            TenantPolicyDTO policy = lifecycleService.getTenantPolicy(tenantId);
            return ResponseEntity.ok(Map.of("success", true, "policy", policy));
        } catch (Exception e) {
            log.error("Error getting tenant policy: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Admin: Update cancellation and refund policy for tenant.
     */
    @PutMapping("/admin/appointment-policy")
    public ResponseEntity<?> updateAdminTenantPolicy(@RequestBody TenantPolicyDTO dto) {
        try {
            User user = getCurrentAuthenticatedUser();
            if (user == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "User not authenticated."));
            }
            Long tenantId = dto.getTenantId() != null ? dto.getTenantId() : (user.getTenant() != null ? user.getTenant().getId() : null);
            if (tenantId == null) {
                tenantId = tenantRepository.findAll().stream().findFirst().map(com.backend.model.Tenant::getId).orElse(null);
            }
            if (tenantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No tenant available to update."));
            }
            TenantPolicyDTO updated = lifecycleService.updateTenantPolicy(tenantId, dto);
            return ResponseEntity.ok(Map.of("success", true, "policy", updated, "message", "Policy updated successfully."));
        } catch (Exception e) {
            log.error("Error updating tenant policy: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Admin: Retry or manually trigger a refund for a cancelled appointment.
     */
    @PostMapping("/admin/appointments/{id}/refund")
    public ResponseEntity<?> processAdminRefund(@PathVariable Long id) {
        try {
            User user = getCurrentAuthenticatedUser();
            String email = user != null ? user.getEmail() : "admin";
            Appointment refunded = lifecycleService.processManualRefund(id, email);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Refund processed successfully.",
                    "refundStatus", refunded.getRefundStatus(),
                    "refundTransactionId", refunded.getRefundTransactionId(),
                    "refundAmount", refunded.getRefundAmount(),
                    "refundCurrency", refunded.getRefundCurrency()
            ));
        } catch (Exception e) {
            log.error("Error processing manual refund for appointment {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Admin/Staff: Manually classify an appointment as No-Show.
     */
    @PostMapping("/admin/appointments/{id}/no-show")
    public ResponseEntity<?> markAppointmentAsNoShow(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            User user = getCurrentAuthenticatedUser();
            String actorName = user != null ? user.getFullName() : "Admin/Staff";
            String actorRole = user != null ? user.getRole() : "admin";
            String reason = body != null && body.containsKey("reason") 
                    ? body.get("reason") 
                    : "Client/Patient failed to attend scheduled appointment.";

            Appointment updated = lifecycleService.markAsNoShow(id, actorRole, actorName, reason);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Appointment successfully classified as No-Show.",
                    "appointmentStatus", updated.getAppointmentStatus(),
                    "refundStatus", updated.getRefundStatus() != null ? updated.getRefundStatus() : "NOT_ELIGIBLE",
                    "refundAmount", updated.getRefundAmount() != null ? updated.getRefundAmount() : 0.0,
                    "settlementStatus", updated.getSettlementStatus() != null ? updated.getSettlementStatus() : "SETTLED",
                    "settlementAmount", updated.getSettlementAmount() != null ? updated.getSettlementAmount() : 0.0
            ));
        } catch (Exception e) {
            log.error("Error marking appointment {} as No-Show: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Admin: Trigger on-demand batch scan of unattended appointments.
     */
    @PostMapping("/admin/appointments/process-no-shows")
    public ResponseEntity<?> processAutomatedNoShows() {
        try {
            int processed = lifecycleService.processAutomatedNoShows();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "count", processed,
                    "message", "Processed " + processed + " unattended appointments as No-Show."
            ));
        } catch (Exception e) {
            log.error("Error during manual No-Show sweep: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Admin/Provider: Reject booking request and process 100% full refund.
     */
    @PostMapping("/admin/appointments/{id}/reject")
    public ResponseEntity<?> rejectAppointmentByAdmin(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            User user = getCurrentAuthenticatedUser();
            String email = user != null ? user.getEmail() : "admin";
            String reason = body != null ? body.get("reason") : "Declined by organization admin";
            Map<String, Object> result = lifecycleService.rejectAppointmentByProvider(id, email, reason);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error rejecting appointment {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
