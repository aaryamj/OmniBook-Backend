package com.backend.service;

import com.backend.model.Tenant;
import com.backend.repository.AppointmentCommissionRepository;
import com.backend.repository.AppointmentRepository;
import com.backend.repository.TenantRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinancialReportService {

    private final TenantRepository tenantRepository;
    private final AppointmentRepository appointmentRepository;
    private final RevenueAnalyticsService revenueAnalyticsService;
    private final AppointmentCommissionRepository appointmentCommissionRepository;
    private final CommissionService commissionService;

    private LocalDateTime parseTimePeriod(String timePeriod) {
        if (timePeriod == null) return null;
        if (timePeriod.startsWith("Last 30 Days")) return LocalDateTime.now().minusDays(30);
        if (timePeriod.startsWith("This Quarter")) return LocalDateTime.now().minusMonths(3);
        if (timePeriod.startsWith("Year to Date")) return LocalDateTime.now().withDayOfYear(1);
        if (timePeriod.startsWith("Last 24 Hours")) return LocalDateTime.now().minusDays(1);
        if (timePeriod.startsWith("Last 7 Days")) return LocalDateTime.now().minusDays(7);
        if (timePeriod.startsWith("This Year")) return LocalDateTime.now().minusYears(1);
        return null; // All time
    }

    public byte[] generateCsvReport(String timePeriod, String reportType) {
        LocalDateTime startDate = parseTimePeriod(timePeriod);
        List<Tenant> tenants = getFilteredTenants(startDate);
        
        StringBuilder sb = new StringBuilder();
        if ("comprehensive".equalsIgnoreCase(reportType)) {
            sb.append("Tenant ID,Organization Name,Subscription Tier,Status,Subscription MRR (NPR),Appointments,Appt Gross (NPR),Platform Commission (NPR),Provider Payout (NPR)\n");
            for (Tenant t : tenants) {
                double mrr = revenueAnalyticsService.calculateTenantMRR(t);
                long appointments = getTenantAppointments(t.getId(), startDate);
                double gross = getTenantGrossAppointments(t.getId(), startDate);
                double commission = getTenantCommission(t.getId(), startDate);
                double payout = gross - commission;

                sb.append(String.format("%d,\"%s\",\"%s\",\"%s\",%.2f,%d,%.2f,%.2f,%.2f\n", 
                    t.getId(), t.getOrganizationName(), t.getSubscriptionTier() != null ? t.getSubscriptionTier() : "Starter", 
                    t.getStatus(), mrr, appointments, gross, commission, payout));
            }
        } else {
            // Payouts
            double defaultRate = commissionService.getCurrentCommissionRate();
            sb.append(String.format("Tenant ID,Organization Name,Total Gross (NPR),Platform Fee (%.1f%%),Net Provider Payout,Status\n", defaultRate));
            for (Tenant t : tenants) {
                double gross = getTenantGrossAppointments(t.getId(), startDate);
                double fee = getTenantCommission(t.getId(), startDate);
                double net = gross - fee;
                sb.append(String.format("%d,\"%s\",%.2f,%.2f,%.2f,Processed\n",
                    t.getId(), t.getOrganizationName(), gross, fee, net));
            }
        }
        return sb.toString().getBytes();
    }

    public byte[] generatePdfReport(String timePeriod, String reportType) {
        LocalDateTime startDate = parseTimePeriod(timePeriod);
        List<Tenant> tenants = getFilteredTenants(startDate);

        Document document = new Document(PageSize.A4.rotate());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();
            
            document.add(new Paragraph("OmniBook Enterprise - Financial & Revenue Report", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18)));
            document.add(new Paragraph("Report Type: " + (reportType.equals("comprehensive") ? "Comprehensive Ledger (Subscription MRR & Commissions)" : "Tenant Payouts Ledger")));
            document.add(new Paragraph("Time Period: " + (timePeriod != null ? timePeriod : "All Time")));
            document.add(new Paragraph("Generated At: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            document.add(new Paragraph(" "));

            if ("comprehensive".equalsIgnoreCase(reportType)) {
                PdfPTable table = new PdfPTable(8);
                table.setWidthPercentage(100);
                table.addCell("ID");
                table.addCell("Organization");
                table.addCell("Plan Tier");
                table.addCell("Status");
                table.addCell("MRR (NPR)");
                table.addCell("Appts");
                table.addCell("Commission (NPR)");
                table.addCell("Provider Payout (NPR)");

                for (Tenant t : tenants) {
                    double mrr = revenueAnalyticsService.calculateTenantMRR(t);
                    long appointments = getTenantAppointments(t.getId(), startDate);
                    double gross = getTenantGrossAppointments(t.getId(), startDate);
                    double commission = getTenantCommission(t.getId(), startDate);
                    double payout = gross - commission;

                    table.addCell(String.valueOf(t.getId()));
                    table.addCell(t.getOrganizationName());
                    table.addCell(t.getSubscriptionTier() != null ? t.getSubscriptionTier() : "Starter");
                    table.addCell(t.getStatus());
                    table.addCell(String.format("%.2f", mrr));
                    table.addCell(String.valueOf(appointments));
                    table.addCell(String.format("%.2f", commission));
                    table.addCell(String.format("%.2f", payout));
                }
                document.add(table);
            } else {
                PdfPTable table = new PdfPTable(6);
                table.setWidthPercentage(100);
                table.addCell("ID");
                table.addCell("Organization");
                table.addCell("Total Gross (NPR)");
                table.addCell("Platform Fee");
                table.addCell("Net Payout (NPR)");
                table.addCell("Status");

                for (Tenant t : tenants) {
                    double gross = getTenantGrossAppointments(t.getId(), startDate);
                    double fee = getTenantCommission(t.getId(), startDate);
                    double net = gross - fee;

                    table.addCell(String.valueOf(t.getId()));
                    table.addCell(t.getOrganizationName());
                    table.addCell(String.format("%.2f", gross));
                    table.addCell(String.format("%.2f", fee));
                    table.addCell(String.format("%.2f", net));
                    table.addCell("Settled");
                }
                document.add(table);
            }

            document.close();
        } catch (DocumentException e) {
            e.printStackTrace();
        }

        return out.toByteArray();
    }

    private List<Tenant> getFilteredTenants(LocalDateTime startDate) {
        List<Tenant> tenants = tenantRepository.findAll();
        if (startDate != null) {
            return tenants.stream()
                    .filter(t -> t.getCreatedAt() != null && !t.getCreatedAt().isBefore(startDate))
                    .collect(Collectors.toList());
        }
        return tenants;
    }

    private long getTenantAppointments(Long tenantId, LocalDateTime startDate) {
        if (startDate != null) {
            return appointmentRepository.countByTenantIdAndCreatedAtAfter(tenantId, startDate);
        }
        return appointmentRepository.countByTenantId(tenantId);
    }

    private double getTenantGrossAppointments(Long tenantId, LocalDateTime startDate) {
        return appointmentRepository.findByTenantId(tenantId).stream()
                .filter(a -> startDate == null || (a.getCreatedAt() != null && !a.getCreatedAt().isBefore(startDate)))
                .filter(a -> "SUCCESS".equalsIgnoreCase(a.getPaymentStatus()) || "PAID".equalsIgnoreCase(a.getPaymentStatus()))
                .mapToDouble(a -> a.getPrice() != null ? a.getPrice() : 0.0)
                .sum();
    }

    private double getTenantCommission(Long tenantId, LocalDateTime startDate) {
        Double comm = appointmentCommissionRepository.sumCommissionByTenant(tenantId);
        if (comm != null && comm > 0.0) return comm;
        double gross = getTenantGrossAppointments(tenantId, startDate);
        double rate = commissionService.getCurrentCommissionRate();
        return Math.round((gross * (rate / 100.0)) * 100.0) / 100.0;
    }
}
