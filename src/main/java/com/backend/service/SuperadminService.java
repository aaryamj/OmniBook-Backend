package com.backend.service;

import com.backend.dto.OnboardClinicRequest;
import com.backend.model.Tenant;
import com.backend.model.User;
import com.backend.repository.AppointmentRepository;
import com.backend.repository.TenantRepository;
import com.backend.repository.UserRepository;
import com.backend.model.Invitation;
import com.backend.repository.InvitationRepository;
import com.backend.dto.SuperadminDashboardDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SuperadminService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;
    private final AppointmentRepository appointmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @Transactional
    public String registerNewClinic(OnboardClinicRequest request) {
        if (tenantRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new RuntimeException("Clinic with this registration number already exists");
        }

        // 1. Create the Tenant
        Tenant newTenant = Tenant.builder()
                .organizationName(request.getClinicName())
                .registrationNumber(request.getRegistrationNumber())
                .subscriptionTier(request.getSubscriptionTier() != null ? request.getSubscriptionTier() : "Enterprise")
                .address(request.getAddress())
                .twoFactorEnabled(request.getEnforce2FA() != null ? request.getEnforce2FA() : false)
                .requireHipaa(request.getRequireHIPAA() != null ? request.getRequireHIPAA() : false)
                .phoneContact(request.getAdminPhone())
                .status("ACTIVE")
                .build();
        
        tenantRepository.save(newTenant);

        // 2. Create the Invitation for this Tenant
        if (userRepository.findByEmail(request.getAdminEmail()).isPresent()) {
            throw new RuntimeException("Admin email already in use");
        }

        String token = UUID.randomUUID().toString();

        Invitation invitation = Invitation.builder()
                .token(token)
                .email(request.getAdminEmail())
                .fullName(request.getAdminFullName())
                .phone(request.getAdminPhone())
                .role("admin") // Clinic Admin
                .tenant(newTenant)
                .expiryDate(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();

        invitationRepository.save(invitation);

        // 3. Send email to root admin with invitation link
        emailService.sendAdminInvite(request.getAdminEmail(), token, request.getClinicName());

        // 4. Create Notification
        notificationService.createNotification(
                "New Clinic Onboarded",
                request.getClinicName() + " has been registered on the platform.",
                "TENANT_REGISTER"
        );

        return "Clinic onboarded successfully. An invitation email has been sent to the clinic administrator.";
    }

    // Example of a global query method bypassing filters
    // This is safe because @Transactional starts the session, but since we haven't set 
    // a tenant context for the superadmin, the Aspect won't enable the filter!
    public long getTotalActiveClinics() {
        return tenantRepository.countActiveClinics();
    }

    private double calculateMRRAtDate(java.util.List<Tenant> tenants, LocalDateTime targetDate) {
        double mrr = 0.0;
        for (Tenant t : tenants) {
            if ("ACTIVE".equalsIgnoreCase(t.getStatus()) && t.getCreatedAt() != null && !t.getCreatedAt().isAfter(targetDate)) {
                String tier = t.getSubscriptionTier() != null ? t.getSubscriptionTier().toLowerCase() : "";
                if (tier.contains("enterprise")) mrr += 30000.0;
                else if (tier.contains("pro") || tier.contains("professional")) mrr += 15000.0;
                else mrr += 5000.0;
            }
        }
        return mrr;
    }

    private LocalDateTime parseTimeFilter(String timeFilter) {
        if (timeFilter == null) return null;
        switch (timeFilter) {
            case "Last 24 Hours":
                return LocalDateTime.now().minusDays(1);
            case "Last 7 Days":
                return LocalDateTime.now().minusDays(7);
            case "Last 30 Days":
                return LocalDateTime.now().minusDays(30);
            case "This Quarter":
                return LocalDateTime.now().minusMonths(3);
            case "This Year":
                return LocalDateTime.now().minusYears(1);
            default:
                return null;
        }
    }

    public SuperadminDashboardDTO getDashboardMetrics(String timeFilter) {
        java.util.List<Tenant> allTenants = tenantRepository.findAll();
        LocalDateTime startDate = parseTimeFilter(timeFilter);
        
        long activeClinics = 0;
        long newClinicsThisWeek = 0;
        double mrr = 0.0;
        
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusDays(7);

        for (Tenant t : allTenants) {
            // Apply time filter
            if (startDate != null && t.getCreatedAt() != null && t.getCreatedAt().isBefore(startDate)) {
                continue;
            }

            if ("ACTIVE".equalsIgnoreCase(t.getStatus())) {
                activeClinics++;
                
                if (t.getCreatedAt() != null && !t.getCreatedAt().isAfter(LocalDateTime.now())) {
                    String tier = t.getSubscriptionTier() != null ? t.getSubscriptionTier().toLowerCase() : "";
                    if (tier.contains("enterprise")) mrr += 30000.0;
                    else if (tier.contains("pro") || tier.contains("professional")) mrr += 15000.0;
                    else mrr += 5000.0;
                }
                
                if (t.getCreatedAt() != null && t.getCreatedAt().isAfter(oneWeekAgo)) {
                    newClinicsThisWeek++;
                }
            }
        }
        
        // Count patient footfall (filtered by time if possible)
        long totalPatientFootfall;
        if (startDate != null) {
            totalPatientFootfall = appointmentRepository.countByCreatedAtAfter(startDate);
        } else {
            totalPatientFootfall = appointmentRepository.count();
        }

        LocalDateTime now = LocalDateTime.now();
        java.util.List<Double> weekData = new java.util.ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            weekData.add(calculateMRRAtDate(allTenants, now.minusDays(i)));
        }

        java.util.List<Double> monthData = new java.util.ArrayList<>();
        for (int i = 3; i >= 0; i--) {
            monthData.add(calculateMRRAtDate(allTenants, now.minusWeeks(i)));
        }

        java.util.List<Double> yearData = new java.util.ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            yearData.add(calculateMRRAtDate(allTenants, now.minusMonths(i)));
        }

        java.util.Map<String, java.util.List<Double>> chartData = new java.util.HashMap<>();
        chartData.put("1W", weekData);
        chartData.put("1M", monthData);
        chartData.put("1Y", yearData);

        return SuperadminDashboardDTO.builder()
                .mrr(mrr)
                .activeClinics(activeClinics)
                .totalPatientFootfall(totalPatientFootfall)
                .systemUptime(99.99)
                .newClinicsThisWeek(newClinicsThisWeek)
                .revenueChartData(chartData)
                .build();
    }

    public java.util.List<com.backend.dto.TenantResponse> getAllTenants(String timeFilter) {
        LocalDateTime startDate = parseTimeFilter(timeFilter);
        
        java.util.List<Tenant> tenants = tenantRepository.findAll();
        if (startDate != null) {
            tenants = tenants.stream()
                .filter(t -> t.getCreatedAt() != null && !t.getCreatedAt().isBefore(startDate))
                .collect(java.util.stream.Collectors.toList());
        }

        return tenants.stream().map(tenant -> {
            String adminEmail = "N/A";
            java.util.List<User> admins = userRepository.findByTenantIdAndRole(tenant.getId(), "admin");
            if (!admins.isEmpty()) {
                adminEmail = admins.get(0).getEmail();
            }
            
            return com.backend.dto.TenantResponse.builder()
                    .id(tenant.getId())
                    .organizationName(tenant.getOrganizationName())
                    .registrationNumber(tenant.getRegistrationNumber())
                    .status(tenant.getStatus())
                    .createdAt(tenant.getCreatedAt())
                    .address(tenant.getAddress())
                    .logoUrl(tenant.getLogoUrl())
                    .primaryAccentColor(tenant.getPrimaryAccentColor())
                    .phoneContact(tenant.getPhoneContact())
                    .timezone(tenant.getTimezone())
                    .openingTime(tenant.getOpeningTime())
                    .closingTime(tenant.getClosingTime())
                    .slotDuration(tenant.getSlotDuration())
                    .legalBusinessName(tenant.getLegalBusinessName())
                    .businessEntityType(tenant.getBusinessEntityType())
                    .ibanAccountNumber(tenant.getIbanAccountNumber())
                    .medicalLicenseUrl(tenant.getMedicalLicenseUrl())
                    .kycVerified(tenant.getKycVerified())
                    .adminEmail(adminEmail)
                    .subscriptionTier(tenant.getSubscriptionTier())
                    .build();
        }).collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public void approveTenant(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
                
        tenant.setKycVerified(true);
        tenant.setStatus("ACTIVE");
        
        tenantRepository.save(tenant);
        
        // Find the admin of this tenant to send the email
        java.util.List<User> admins = userRepository.findByTenantIdAndRole(tenantId, "admin");
        if (!admins.isEmpty()) {
            emailService.sendTenantApprovalEmail(admins.get(0).getEmail(), tenant.getOrganizationName());
        }

        notificationService.createNotification(
                "Clinic Approved",
                tenant.getOrganizationName() + " has been verified and fully activated.",
                "TENANT_APPROVE"
        );
    }
    @Transactional
    public void suspendTenant(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        
        tenant.setStatus("SUSPENDED");
        tenantRepository.save(tenant);
        
        notificationService.createNotification(
                "Clinic Suspended",
                tenant.getOrganizationName() + " has been suspended.",
                "TENANT_SUSPEND"
        );
    }

    @Transactional
    public void reactivateTenant(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        
        tenant.setStatus("ACTIVE");
        tenantRepository.save(tenant);
        
        notificationService.createNotification(
                "Clinic Reactivated",
                tenant.getOrganizationName() + " has been reactivated.",
                "TENANT_REACTIVATE"
        );
    }
}
