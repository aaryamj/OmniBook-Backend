package com.backend.controller;

import com.backend.dto.OnboardClinicRequest;
import com.backend.dto.SuperadminDashboardDTO;
import com.backend.model.AuditLog;
import com.backend.service.AuditLogService;
import com.backend.service.SuperadminService;
import com.backend.service.SystemKPIService;
import com.backend.dto.SystemKPIDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/superadmin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperadminController {

    private final SuperadminService superadminService;
    private final AuditLogService auditLogService;
    private final SystemKPIService systemKPIService;
    private final com.backend.service.FinancialReportService financialReportService;
    private final com.backend.service.SupportTicketService supportTicketService;
    private final com.backend.service.SubscriptionPlanService subscriptionPlanService;
    private final com.backend.service.SubscriptionService subscriptionService;
    private final com.backend.service.CommissionService commissionService;

    @PostMapping("/tenants")
    public ResponseEntity<String> onboardClinic(@RequestBody OnboardClinicRequest request) {
        String result = superadminService.registerNewClinic(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/metrics/clinics")
    public ResponseEntity<Long> getTotalClinics() {
        long total = superadminService.getTotalActiveClinics();
        return ResponseEntity.ok(total);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<SuperadminDashboardDTO> getDashboard(
            @RequestParam(required = false, defaultValue = "Last 30 Days") String timeFilter) {
        return ResponseEntity.ok(superadminService.getDashboardMetrics(timeFilter));
    }

    @GetMapping("/tenants")
    public ResponseEntity<java.util.List<com.backend.dto.TenantResponse>> getAllTenants(
            @RequestParam(required = false) String timeFilter) {
        return ResponseEntity.ok(superadminService.getAllTenants(timeFilter));
    }

    @PutMapping("/tenants/{id}/approve")
    public ResponseEntity<Void> approveTenant(@PathVariable Long id) {
        superadminService.approveTenant(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/tenants/{id}/suspend")
    public ResponseEntity<Void> suspendTenant(@PathVariable Long id) {
        superadminService.suspendTenant(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/tenants/{id}/reactivate")
    public ResponseEntity<Void> reactivateTenant(@PathVariable Long id) {
        superadminService.reactivateTenant(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<java.util.List<AuditLog>> getGlobalAuditLogs(
            @RequestParam(required = false) String timeFilter) {
        return ResponseEntity.ok(auditLogService.getGlobalLogsFiltered(timeFilter));
    }

    @GetMapping("/audit-dashboard")
    public ResponseEntity<com.backend.dto.GlobalAuditDashboardDTO> getAuditDashboard(
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "Last 30 Days") String timeFilter) {
        // You could pull uptime from systemKPIService.getCurrentKPIs().getUptime() if you added it there, 
        // but for now we'll pass a default or calculate it if needed. 
        // We'll just pass 99.9 for the gauge.
        return ResponseEntity.ok(auditLogService.getGlobalDashboardMetrics(99.9, timeFilter));
    }

    @GetMapping("/system-kpi")
    public ResponseEntity<SystemKPIDTO> getSystemKPIs(
            @RequestParam(required = false, defaultValue = "Real-Time (Live)") String timeRange) {
        return ResponseEntity.ok(systemKPIService.getCurrentKPIs(timeRange));
    }

    @GetMapping("/reports/financial")
    public ResponseEntity<byte[]> getFinancialReport(
            @RequestParam(required = false) String timePeriod,
            @RequestParam(required = false, defaultValue = "comprehensive") String reportType,
            @RequestParam(required = false, defaultValue = "csv") String format) {
        
        byte[] data;
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        
        if ("pdf".equalsIgnoreCase(format)) {
            data = financialReportService.generatePdfReport(timePeriod, reportType);
            headers.set(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/pdf");
            headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"financial-report.pdf\"");
        } else {
            data = financialReportService.generateCsvReport(timePeriod, reportType);
            headers.set(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv");
            headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"financial-report.csv\"");
        }
        
        return new ResponseEntity<>(data, headers, org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/support/tickets")
    public ResponseEntity<java.util.List<com.backend.dto.SupportTicketResponseDTO>> getSupportTickets(
            @RequestParam(required = false) String timeFilter,
            @RequestParam(required = false) String issueType) {
        return ResponseEntity.ok(supportTicketService.getTickets(timeFilter, issueType));
    }

    @GetMapping("/support/kpis")
    public ResponseEntity<com.backend.dto.SupportKPIDTO> getSupportKPIs(
            @RequestParam(required = false) String timeFilter) {
        return ResponseEntity.ok(supportTicketService.getSupportKPIs(timeFilter));
    }

    @PatchMapping("/support/tickets/{id}/status")
    public ResponseEntity<com.backend.dto.SupportTicketResponseDTO> updateTicketStatus(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> payload) {
        String newStatus = payload.get("status");
        if (newStatus == null || newStatus.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(supportTicketService.updateTicketStatus(id, newStatus));
    }

    @PostMapping("/support/announcements")
    public ResponseEntity<com.backend.model.SystemAnnouncement> createAnnouncement(
            @jakarta.validation.Valid @RequestBody com.backend.dto.AnnouncementRequestDTO request,
            java.security.Principal principal) {
        String createdBy = (principal != null) ? principal.getName() : "System Admin";
        return ResponseEntity.ok(supportTicketService.createAnnouncement(request, createdBy));
    }

    @GetMapping("/support/announcements")
    public ResponseEntity<java.util.List<com.backend.model.SystemAnnouncement>> getAnnouncements() {
        return ResponseEntity.ok(supportTicketService.getAllAnnouncements());
    }

    @GetMapping("/rbac")
    public ResponseEntity<com.backend.dto.SuperadminRBACDTO> getRBAC() {
        return ResponseEntity.ok(superadminService.getRBACData());
    }

    @PostMapping("/rbac/roles")
    public ResponseEntity<com.backend.dto.SuperadminRBACDTO.TenantRoleItemDTO> createRole(
            @jakarta.validation.Valid @RequestBody com.backend.dto.SuperadminCreateRoleRequestDTO request) {
        return ResponseEntity.ok(superadminService.createSuperadminCustomRole(request));
    }

    @GetMapping("/search")
    public ResponseEntity<java.util.List<com.backend.dto.GlobalSearchResultDTO>> globalSearch(
            @RequestParam(name = "q", defaultValue = "") String query) {
        return ResponseEntity.ok(superadminService.globalSearch(query));
    }

    // ==========================================
    // SUBSCRIPTION PLAN MANAGEMENT
    // ==========================================

    @GetMapping("/plans")
    public ResponseEntity<java.util.List<com.backend.dto.SubscriptionPlanDTO>> getAllPlans() {
        return ResponseEntity.ok(subscriptionPlanService.getAllPlans());
    }

    @PostMapping("/plans")
    public ResponseEntity<com.backend.dto.SubscriptionPlanDTO> createPlan(
            @jakarta.validation.Valid @RequestBody com.backend.dto.SubscriptionPlanDTO dto) {
        return ResponseEntity.ok(subscriptionPlanService.createPlan(dto));
    }

    @PutMapping("/plans/{id}")
    public ResponseEntity<com.backend.dto.SubscriptionPlanDTO> updatePlan(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody com.backend.dto.SubscriptionPlanDTO dto) {
        return ResponseEntity.ok(subscriptionPlanService.updatePlan(id, dto));
    }

    @PatchMapping("/plans/{id}/toggle")
    public ResponseEntity<com.backend.dto.SubscriptionPlanDTO> togglePlanStatus(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionPlanService.togglePlanStatus(id));
    }

    // ==========================================
    // TENANT SUBSCRIPTIONS MANAGEMENT
    // ==========================================

    @GetMapping("/subscriptions/tenants")
    public ResponseEntity<java.util.List<com.backend.dto.SuperadminSubscriptionTenantDTO>> getTenantSubscriptions() {
        return ResponseEntity.ok(subscriptionService.getSuperadminTenantSubscriptions());
    }

    @PostMapping("/subscriptions/tenants/{id}/suspend")
    public ResponseEntity<?> manualSuspendSubscription(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> payload,
            java.security.Principal principal) {
        try {
            String reason = payload != null ? payload.get("reason") : "Suspended by Super Admin";
            String admin = principal != null ? principal.getName() : "Super Admin";
            subscriptionService.manualSuspendSubscription(id, reason, admin);
            return ResponseEntity.ok(java.util.Map.of("message", "Subscription suspended successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/subscriptions/tenants/{id}/reactivate")
    public ResponseEntity<?> manualReactivateSubscription(
            @PathVariable Long id,
            java.security.Principal principal) {
        try {
            String admin = principal != null ? principal.getName() : "Super Admin";
            subscriptionService.manualReactivateSubscription(id, admin);
            return ResponseEntity.ok(java.util.Map.of("message", "Subscription reactivated successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/subscriptions/tenants/{id}/extend")
    public ResponseEntity<?> manualExtendSubscription(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> payload,
            java.security.Principal principal) {
        try {
            int days = Integer.parseInt(payload.get("days").toString());
            String reason = payload.get("reason") != null ? payload.get("reason").toString() : "Direct administrative extension";
            String admin = principal != null ? principal.getName() : "Super Admin";
            subscriptionService.manualExtendSubscription(id, days, reason, admin);
            return ResponseEntity.ok(java.util.Map.of("message", "Subscription extended successfully by " + days + " days"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    // ==========================================
    // EXTENSION REQUESTS QUEUE
    // ==========================================

    @GetMapping("/subscriptions/extension-requests")
    public ResponseEntity<java.util.List<com.backend.dto.SubscriptionExtensionRequestDTO>> getAllExtensionRequests() {
        return ResponseEntity.ok(subscriptionService.getAllExtensionRequests());
    }

    @PostMapping("/subscriptions/extension-requests/{id}/review")
    public ResponseEntity<?> reviewExtensionRequest(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> payload,
            java.security.Principal principal) {
        try {
            boolean approved = Boolean.parseBoolean(payload.get("approved").toString());
            Integer approvedDays = payload.get("approvedDays") != null ? Integer.parseInt(payload.get("approvedDays").toString()) : null;
            String notes = payload.get("notes") != null ? payload.get("notes").toString() : null;
            String admin = principal != null ? principal.getName() : "Super Admin";
            return ResponseEntity.ok(subscriptionService.reviewExtensionRequest(id, approved, approvedDays, notes, admin));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    // ==========================================
    // APPOINTMENT COMMISSION RATE
    // ==========================================

    @PutMapping("/settings/commission-rate")
    public ResponseEntity<?> updateCommissionRate(
            @RequestBody java.util.Map<String, Object> payload) {
        try {
            Double rate = Double.parseDouble(payload.get("rate").toString());
            return ResponseEntity.ok(commissionService.updateCommissionRate(rate));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }
}

