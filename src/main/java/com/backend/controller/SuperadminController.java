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
    public ResponseEntity<SystemKPIDTO> getSystemKPIs() {
        return ResponseEntity.ok(systemKPIService.getCurrentKPIs());
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
}
