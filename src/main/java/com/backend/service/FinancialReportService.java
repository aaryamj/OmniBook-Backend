package com.backend.service;

import com.backend.model.Tenant;
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
            sb.append("Tenant ID,Clinic Name,Subscription Tier,Status,MRR (NPR),Total Appointments\n");
            for (Tenant t : tenants) {
                double mrr = calculateTenantMRR(t);
                long appointments = getTenantAppointments(t.getId(), startDate);
                sb.append(String.format("%d,\"%s\",\"%s\",\"%s\",%.2f,%d\n", 
                    t.getId(), t.getOrganizationName(), t.getSubscriptionTier(), t.getStatus(), mrr, appointments));
            }
        } else {
            // Payouts
            sb.append("Tenant ID,Clinic Name,Total Revenue (NPR),Platform Fee (5%),Net Payout,Status\n");
            for (Tenant t : tenants) {
                long appointments = getTenantAppointments(t.getId(), startDate);
                // Simulate an average ticket size of 1500 NPR per appointment
                double revenue = appointments * 1500.0;
                double fee = revenue * 0.05;
                double net = revenue - fee;
                sb.append(String.format("%d,\"%s\",%.2f,%.2f,%.2f,Pending\n",
                    t.getId(), t.getOrganizationName(), revenue, fee, net));
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
            
            document.add(new Paragraph("OmniBook Enterprise - Financial Report", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18)));
            document.add(new Paragraph("Report Type: " + (reportType.equals("comprehensive") ? "Comprehensive Ledger" : "Tenant Payouts")));
            document.add(new Paragraph("Time Period: " + (timePeriod != null ? timePeriod : "All Time")));
            document.add(new Paragraph("Generated At: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            document.add(new Paragraph(" "));

            if ("comprehensive".equalsIgnoreCase(reportType)) {
                PdfPTable table = new PdfPTable(6);
                table.setWidthPercentage(100);
                table.addCell("Tenant ID");
                table.addCell("Clinic Name");
                table.addCell("Subscription Tier");
                table.addCell("Status");
                table.addCell("MRR (NPR)");
                table.addCell("Appointments");

                for (Tenant t : tenants) {
                    double mrr = calculateTenantMRR(t);
                    long appointments = getTenantAppointments(t.getId(), startDate);
                    table.addCell(String.valueOf(t.getId()));
                    table.addCell(t.getOrganizationName());
                    table.addCell(t.getSubscriptionTier() != null ? t.getSubscriptionTier() : "None");
                    table.addCell(t.getStatus());
                    table.addCell(String.format("%.2f", mrr));
                    table.addCell(String.valueOf(appointments));
                }
                document.add(table);
            } else {
                PdfPTable table = new PdfPTable(6);
                table.setWidthPercentage(100);
                table.addCell("Tenant ID");
                table.addCell("Clinic Name");
                table.addCell("Total Revenue");
                table.addCell("Platform Fee (5%)");
                table.addCell("Net Payout");
                table.addCell("Status");

                for (Tenant t : tenants) {
                    long appointments = getTenantAppointments(t.getId(), startDate);
                    double revenue = appointments * 1500.0;
                    double fee = revenue * 0.05;
                    double net = revenue - fee;
                    table.addCell(String.valueOf(t.getId()));
                    table.addCell(t.getOrganizationName());
                    table.addCell(String.format("%.2f", revenue));
                    table.addCell(String.format("%.2f", fee));
                    table.addCell(String.format("%.2f", net));
                    table.addCell("Pending");
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

    private double calculateTenantMRR(Tenant t) {
        if (!"ACTIVE".equalsIgnoreCase(t.getStatus())) return 0.0;
        String tier = t.getSubscriptionTier() != null ? t.getSubscriptionTier().toLowerCase() : "";
        if (tier.contains("enterprise")) return 30000.0;
        if (tier.contains("pro") || tier.contains("professional")) return 15000.0;
        return 5000.0;
    }

    private long getTenantAppointments(Long tenantId, LocalDateTime startDate) {
        if (startDate != null) {
            return appointmentRepository.countByTenantIdAndCreatedAtAfter(tenantId, startDate);
        }
        return appointmentRepository.countByTenantId(tenantId);
    }
}
