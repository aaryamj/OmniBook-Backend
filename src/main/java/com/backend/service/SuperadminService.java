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
    private final com.backend.repository.TenantRoleRepository tenantRoleRepository;
    private final com.backend.repository.PlatformInvoiceRepository platformInvoiceRepository;
    private final com.backend.repository.SupportTicketRepository supportTicketRepository;

    @Transactional
    public String registerNewClinic(OnboardClinicRequest request) {
        if (tenantRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new RuntimeException("Clinic with this registration number already exists");
        }

        // 1. Create the Tenant
        Tenant newTenant = Tenant.builder()
                .organizationName(request.getOrganizationName())
                .organizationType(request.getOrganizationType() != null ? request.getOrganizationType() : "Clinic")
                .registrationNumber(request.getRegistrationNumber())
                .subscriptionTier(request.getSubscriptionTier() != null ? request.getSubscriptionTier() : "Enterprise")
                .address(request.getAddress())
                .twoFactorEnabled(request.getEnforce2FA() != null ? request.getEnforce2FA() : false)
                .requireHipaa(request.getRequireHIPAA() != null ? request.getRequireHIPAA() : false)
                .phoneContact(request.getAdminPhone())
                .status("PENDING_SETUP")
                .kycVerified(false)
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
        emailService.sendAdminInvite(request.getAdminEmail(), token, request.getOrganizationName(), newTenant.getOrganizationType());

        // 4. Create Notification
        notificationService.createNotification(
                "New Clinic Onboarded",
                request.getOrganizationName() + " has been registered on the platform.",
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
        String normalized = timeFilter.trim().toLowerCase();
        java.time.LocalDate today = java.time.LocalDate.now();
        switch (normalized) {
            case "today":
                return today.atStartOfDay();
            case "last 24 hours":
                return LocalDateTime.now().minusDays(1);
            case "last 7 days":
                return LocalDateTime.now().minusDays(7);
            case "last 30 days":
                return LocalDateTime.now().minusDays(30);
            case "this quarter":
                int firstMonthOfQuarter = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                return java.time.LocalDate.of(today.getYear(), firstMonthOfQuarter, 1).atStartOfDay();
            case "this year":
                return java.time.LocalDate.of(today.getYear(), 1, 1).atStartOfDay();
            case "all time":
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
                
                if (t.getCreatedAt() != null && (startDate == null || !t.getCreatedAt().isAfter(LocalDateTime.now()))) {
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
            } else {
                java.util.List<Invitation> pendingInvites = invitationRepository.findByTenantId(tenant.getId());
                if (!pendingInvites.isEmpty()) {
                    adminEmail = pendingInvites.get(0).getEmail();
                }
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
        String recipientEmail = null;
        java.util.List<User> admins = userRepository.findByTenantIdAndRole(tenantId, "admin");
        if (!admins.isEmpty() && admins.get(0).getEmail() != null) {
            recipientEmail = admins.get(0).getEmail();
        } else {
            java.util.List<Invitation> invites = invitationRepository.findByTenantId(tenantId);
            if (!invites.isEmpty()) {
                recipientEmail = invites.get(0).getEmail();
            }
        }

        if (recipientEmail != null && !recipientEmail.isBlank()) {
            emailService.sendTenantApprovalEmail(recipientEmail, tenant.getOrganizationName(), tenant.getOrganizationType());
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

    public com.backend.dto.SuperadminRBACDTO getRBACData() {
        long customRolesCount = tenantRoleRepository.count();
        long totalRoles = 4 + customRolesCount; // Base system roles: Superadmin, Admin, Provider, User + custom
        long activeTenants = tenantRepository.countActiveClinics();

        long totalTenants = tenantRepository.count();
        long mfaTenants = tenantRepository.findAll().stream()
                .filter(t -> Boolean.TRUE.equals(t.getTwoFactorEnabled()))
                .count();
        String mfaEnforcement = totalTenants > 0 
                ? Math.round(((double) mfaTenants / totalTenants) * 100) + "%" 
                : "100%";

        // 1. System Roles Tab
        java.util.List<String> systemColumns = java.util.List.of(
                "Super Administrator",
                "Tenant Administrator",
                "Service Provider / Staff",
                "Client / Customer"
        );

        java.util.List<com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO> systemRows = java.util.List.of(
                com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                        .feature("Platform Governance & Tenant Provisioning")
                        .icon("admin_panel_settings")
                        .module("Platform")
                        .permissions(java.util.List.of(true, false, false, false))
                        .build(),
                com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                        .feature("Configure Organization & Billing Settings")
                        .icon("settings")
                        .module("Settings")
                        .permissions(java.util.List.of(true, true, false, false))
                        .build(),
                com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                        .feature("Manage Staff Rosters & Delegation")
                        .icon("groups")
                        .module("Staff")
                        .permissions(java.util.List.of(true, true, true, false))
                        .build(),
                com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                        .feature("Customer & Student CRM Records")
                        .icon("folder_shared")
                        .module("CRM")
                        .permissions(java.util.List.of(true, true, true, false))
                        .build(),
                com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                        .feature("Financial Ledger, Invoices & Gateways")
                        .icon("payments")
                        .module("Financials")
                        .permissions(java.util.List.of(true, true, false, false))
                        .build(),
                com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                        .feature("Book & Manage Appointments / Sessions")
                        .icon("calendar_month")
                        .module("Scheduling")
                        .permissions(java.util.List.of(true, true, true, true))
                        .build(),
                com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                        .feature("View Cluster Telemetry & System KPIs")
                        .icon("monitoring")
                        .module("Telemetry")
                        .permissions(java.util.List.of(true, false, false, false))
                        .build()
        );

        // 2. Real Tenant Custom Roles from DB
        java.util.List<com.backend.model.TenantRole> dbRoles = tenantRoleRepository.findAll();
        java.util.List<com.backend.dto.SuperadminRBACDTO.TenantRoleItemDTO> tenantRoleDTOs = dbRoles.stream().map(r -> {
            long assigned = 0;
            if (r.getTenant() != null) {
                assigned = userRepository.findByTenantId(r.getTenant().getId()).stream()
                        .filter(u -> u.getTenantRole() != null && u.getTenantRole().getId().equals(r.getId()))
                        .count();
            }
            return com.backend.dto.SuperadminRBACDTO.TenantRoleItemDTO.builder()
                    .id(r.getId())
                    .roleName(r.getRoleName())
                    .tenantName(r.getTenant() != null ? r.getTenant().getOrganizationName() : "Platform Default")
                    .tenantType(r.getTenant() != null && r.getTenant().getOrganizationType() != null ? r.getTenant().getOrganizationType() : "Clinic")
                    .accessScope(r.getAccessScope())
                    .privilegeLevel(r.getPrivilegeLevel())
                    .assignedUsers(assigned)
                    .build();
        }).collect(java.util.stream.Collectors.toList());

        // 3. Multi-Organization Industry Role Templates
        java.util.Map<String, com.backend.dto.SuperadminRBACDTO.IndustryTemplateDTO> templates = new java.util.HashMap<>();

        // A. Education / College Template
        templates.put("Education / College", com.backend.dto.SuperadminRBACDTO.IndustryTemplateDTO.builder()
                .columns(java.util.List.of("Dean / Principal", "Faculty / Instructor", "Admissions Officer", "Student"))
                .rows(java.util.List.of(
                        com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                                .feature("Academic Curriculum & Session Rostering")
                                .icon("school")
                                .module("Academics")
                                .permissions(java.util.List.of(true, true, false, false))
                                .build(),
                        com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                                .feature("Student Enrollment & Grade Records")
                                .icon("assignment_ind")
                                .module("Students")
                                .permissions(java.util.List.of(true, true, true, false))
                                .build(),
                        com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                                .feature("Tuition Fees, Grants & Billing")
                                .icon("receipt_long")
                                .module("Bursar")
                                .permissions(java.util.List.of(true, false, true, false))
                                .build(),
                        com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                                .feature("Book Consultation & Class Sessions")
                                .icon("event_available")
                                .module("Scheduling")
                                .permissions(java.util.List.of(true, true, false, true))
                                .build()
                ))
                .build()
        );

        // B. Healthcare / Clinic Template
        templates.put("Healthcare / Clinic", com.backend.dto.SuperadminRBACDTO.IndustryTemplateDTO.builder()
                .columns(java.util.List.of("Chief Medical Officer", "Attending Physician", "Resident / Nurse", "Patient"))
                .rows(java.util.List.of(
                        com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                                .feature("Write Clinical Notes & EMR Records")
                                .icon("medical_information")
                                .module("EMR")
                                .permissions(java.util.List.of(true, true, true, false))
                                .build(),
                        com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                                .feature("Prescribe Medication (e-Rx)")
                                .icon("prescriptions")
                                .module("Pharmacy")
                                .permissions(java.util.List.of(true, true, false, false))
                                .build(),
                        com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                                .feature("Medical Billing & Insurance ICD-10")
                                .icon("health_and_safety")
                                .module("Billing")
                                .permissions(java.util.List.of(true, false, false, false))
                                .build(),
                        com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                                .feature("Book Clinic Appointment / Consult")
                                .icon("calendar_month")
                                .module("Appointments")
                                .permissions(java.util.List.of(true, true, true, true))
                                .build()
                ))
                .build()
        );

        // C. Beauty & Wellness / Salon Template
        templates.put("Beauty & Wellness", com.backend.dto.SuperadminRBACDTO.IndustryTemplateDTO.builder()
                .columns(java.util.List.of("Salon Owner", "Lead Stylist / Specialist", "Front Desk Reception", "Client"))
                .rows(java.util.List.of(
                        com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                                .feature("Treatment & Service Catalog Setup")
                                .icon("spa")
                                .module("Services")
                                .permissions(java.util.List.of(true, false, false, false))
                                .build(),
                        com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                                .feature("Stylist Stations & Shift Rostering")
                                .icon("chair")
                                .module("Stylists")
                                .permissions(java.util.List.of(true, true, false, false))
                                .build(),
                        com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                                .feature("POS Checkout & Retail Product Sales")
                                .icon("point_of_sale")
                                .module("Checkout")
                                .permissions(java.util.List.of(true, false, true, false))
                                .build(),
                        com.backend.dto.SuperadminRBACDTO.RBACFeatureRowDTO.builder()
                                .feature("Book Treatment & Salon Services")
                                .icon("content_cut")
                                .module("Bookings")
                                .permissions(java.util.List.of(true, true, true, true))
                                .build()
                ))
                .build()
        );

        return com.backend.dto.SuperadminRBACDTO.builder()
                .totalRoles(totalRoles)
                .twoFactorEnforcement(mfaEnforcement)
                .activeTenantsCount(activeTenants)
                .securityPosture("Strict")
                .systemRoleColumns(systemColumns)
                .systemFeatureRows(systemRows)
                .tenantCustomRoles(tenantRoleDTOs)
                .industryTemplates(templates)
                .build();
    }

    @Transactional
    public com.backend.dto.SuperadminRBACDTO.TenantRoleItemDTO createSuperadminCustomRole(com.backend.dto.SuperadminCreateRoleRequestDTO request) {
        java.util.List<Tenant> tenants = tenantRepository.findAll();
        Tenant tenant = tenants.isEmpty() ? null : tenants.get(0);

        if (tenant == null) {
            throw new RuntimeException("No active tenants found to anchor custom role");
        }

        com.backend.model.TenantRole role = com.backend.model.TenantRole.builder()
                .tenant(tenant)
                .roleName(request.getRoleTitle() != null ? request.getRoleTitle() : "Custom Role")
                .accessScope(request.getRoleScope() != null ? request.getRoleScope() : "Global Platform")
                .privilegeLevel(Boolean.TRUE.equals(request.getRequireMFA()) ? "HIGH_PRIVILEGE" : "STANDARD")
                .build();

        com.backend.model.TenantRole saved = tenantRoleRepository.save(role);

        notificationService.createNotification(
                "Role Created",
                "New role " + role.getRoleName() + " has been published to the system.",
                "ROLE_CREATE"
        );

        return com.backend.dto.SuperadminRBACDTO.TenantRoleItemDTO.builder()
                .id(saved.getId())
                .roleName(saved.getRoleName())
                .tenantName(tenant.getOrganizationName())
                .tenantType(tenant.getOrganizationType() != null ? tenant.getOrganizationType() : "General")
                .accessScope(saved.getAccessScope())
                .privilegeLevel(saved.getPrivilegeLevel())
                .assignedUsers(0)
                .build();
    }

    public java.util.List<com.backend.dto.GlobalSearchResultDTO> globalSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        String q = query.trim().toLowerCase();
        java.util.List<com.backend.dto.GlobalSearchResultDTO> results = new java.util.ArrayList<>();

        // 1. Search Tenants & Organizations
        java.util.List<Tenant> allTenants = tenantRepository.findAll();
        int tenantCount = 0;
        for (Tenant t : allTenants) {
            if (tenantCount >= 10) break;
            boolean matches = (t.getOrganizationName() != null && t.getOrganizationName().toLowerCase().contains(q)) ||
                              (t.getOrganizationType() != null && t.getOrganizationType().toLowerCase().contains(q)) ||
                              (t.getRegistrationNumber() != null && t.getRegistrationNumber().toLowerCase().contains(q)) ||
                              (t.getAddress() != null && t.getAddress().toLowerCase().contains(q)) ||
                              (t.getPhoneContact() != null && t.getPhoneContact().toLowerCase().contains(q)) ||
                              (t.getStatus() != null && t.getStatus().toLowerCase().contains(q));
            if (matches) {
                String sub = (t.getOrganizationType() != null ? t.getOrganizationType() : "Organization") + 
                             " • " + (t.getSubscriptionTier() != null ? t.getSubscriptionTier() : "Standard") + 
                             (t.getAddress() != null ? " • " + t.getAddress() : "");
                results.add(com.backend.dto.GlobalSearchResultDTO.builder()
                        .id("tenant-" + t.getId())
                        .category("Tenants & Organizations")
                        .title(t.getOrganizationName() != null ? t.getOrganizationName() : "Tenant #" + t.getId())
                        .subtitle(sub)
                        .status(t.getStatus() != null ? t.getStatus() : "ACTIVE")
                        .link("/superadmin/tenants?search=" + java.net.URLEncoder.encode(t.getOrganizationName() != null ? t.getOrganizationName() : String.valueOf(t.getId()), java.nio.charset.StandardCharsets.UTF_8))
                        .icon("apartment")
                        .build());
                tenantCount++;
            }
        }

        // 2. Search Administrators (System Admins and Tenant Admins only)
        java.util.List<User> admins = userRepository.findAll().stream()
                .filter(u -> "admin".equalsIgnoreCase(u.getRole()) || "super_admin".equalsIgnoreCase(u.getRole()))
                .filter(u -> (u.getFullName() != null && u.getFullName().toLowerCase().contains(q)) ||
                             (u.getEmail() != null && u.getEmail().toLowerCase().contains(q)) ||
                             (u.getPhone() != null && u.getPhone().toLowerCase().contains(q)))
                .limit(8)
                .toList();

        for (User u : admins) {
            String orgName = u.getTenant() != null ? u.getTenant().getOrganizationName() : "Platform SuperAdmin";
            results.add(com.backend.dto.GlobalSearchResultDTO.builder()
                    .id("admin-" + u.getId())
                    .category("Administrators")
                    .title(u.getFullName() != null ? u.getFullName() : u.getEmail())
                    .subtitle(orgName + " • " + u.getEmail())
                    .status(u.isEnabled() ? "Active" : "Disabled")
                    .link("/superadmin/tenants")
                    .icon("manage_accounts")
                    .build());
        }

        // 3. Search Platform Invoices
        if (platformInvoiceRepository != null) {
            java.util.List<com.backend.model.PlatformInvoice> invoices = platformInvoiceRepository.findAllByOrderByCreatedAtDesc();
            int invCount = 0;
            for (com.backend.model.PlatformInvoice inv : invoices) {
                if (invCount >= 6) break;
                boolean matches = (inv.getInvoiceNumber() != null && inv.getInvoiceNumber().toLowerCase().contains(q)) ||
                                  (inv.getOrganizationName() != null && inv.getOrganizationName().toLowerCase().contains(q)) ||
                                  (inv.getPlanName() != null && inv.getPlanName().toLowerCase().contains(q)) ||
                                  (inv.getStatus() != null && inv.getStatus().toLowerCase().contains(q));
                if (matches) {
                    String sub = (inv.getCurrency() != null ? inv.getCurrency() : "NPR") + " " + inv.getAmount() + 
                                 " • " + (inv.getOrganizationName() != null ? inv.getOrganizationName() : "") +
                                 " • " + (inv.getPlanName() != null ? inv.getPlanName() : "");
                    results.add(com.backend.dto.GlobalSearchResultDTO.builder()
                            .id("invoice-" + inv.getId())
                            .category("Platform Invoices")
                            .title(inv.getInvoiceNumber())
                            .subtitle(sub.trim())
                            .status(inv.getStatus() != null ? inv.getStatus() : "PAID")
                            .link("/superadmin/dashboard")
                            .icon("receipt_long")
                            .build());
                    invCount++;
                }
            }
        }

        // 4. Search Support Tickets
        if (supportTicketRepository != null) {
            java.util.List<com.backend.model.SupportTicket> tickets = supportTicketRepository.findAllByOrderByCreatedAtDesc();
            int ticketCount = 0;
            for (com.backend.model.SupportTicket st : tickets) {
                if (ticketCount >= 6) break;
                boolean matches = (st.getTicketNumber() != null && st.getTicketNumber().toLowerCase().contains(q)) ||
                                  (st.getSubject() != null && st.getSubject().toLowerCase().contains(q)) ||
                                  (st.getRequesterName() != null && st.getRequesterName().toLowerCase().contains(q)) ||
                                  (st.getAdminAccount() != null && st.getAdminAccount().toLowerCase().contains(q)) ||
                                  (st.getStatus() != null && st.getStatus().toLowerCase().contains(q));
                if (matches) {
                    results.add(com.backend.dto.GlobalSearchResultDTO.builder()
                            .id("ticket-" + st.getId())
                            .category("Support Tickets")
                            .title(st.getTicketNumber() + ": " + (st.getSubject() != null ? st.getSubject() : "Inquiry"))
                            .subtitle((st.getRequesterName() != null ? st.getRequesterName() : "User") + " • Priority: " + (st.getPriority() != null ? st.getPriority() : "Normal"))
                            .status(st.getStatus() != null ? st.getStatus() : "OPEN")
                            .link("/superadmin/support")
                            .icon("confirmation_number")
                            .build());
                    ticketCount++;
                }
            }
        }

        // 5. Search RBAC Roles
        if (tenantRoleRepository != null) {
            java.util.List<com.backend.model.TenantRole> roles = tenantRoleRepository.findAll();
            for (com.backend.model.TenantRole r : roles) {
                boolean matches = (r.getRoleName() != null && r.getRoleName().toLowerCase().contains(q)) ||
                                  (r.getAccessScope() != null && r.getAccessScope().toLowerCase().contains(q));
                if (matches) {
                    results.add(com.backend.dto.GlobalSearchResultDTO.builder()
                            .id("role-" + r.getId())
                            .category("Roles & Permissions")
                            .title(r.getRoleName())
                            .subtitle("Scope: " + (r.getAccessScope() != null ? r.getAccessScope() : "Tenant"))
                            .status("RBAC")
                            .link("/superadmin/permissions")
                            .icon("admin_panel_settings")
                            .build());
                }
            }
        }

        // 6. SuperAdmin Navigation shortcuts
        java.util.List<com.backend.dto.GlobalSearchResultDTO> superNav = java.util.List.of(
                com.backend.dto.GlobalSearchResultDTO.builder().id("nav-tenants").category("Navigation").title("Tenant Management").subtitle("Onboard and govern healthcare clinics & colleges").status("Module").link("/superadmin/tenants").icon("apartment").build(),
                com.backend.dto.GlobalSearchResultDTO.builder().id("nav-kpi").category("Navigation").title("Platform KPIs & Metrics").subtitle("System telemetry, uptime, and database performance").status("Module").link("/superadmin/system-kpi").icon("monitoring").build(),
                com.backend.dto.GlobalSearchResultDTO.builder().id("nav-support").category("Navigation").title("Support Desk & Tickets").subtitle("Handle customer queries, alerts, and SLA").status("Module").link("/superadmin/support").icon("support_agent").build(),
                com.backend.dto.GlobalSearchResultDTO.builder().id("nav-permissions").category("Navigation").title("Roles & RBAC Permissions").subtitle("Manage custom organizational roles and access control").status("Module").link("/superadmin/permissions").icon("security").build(),
                com.backend.dto.GlobalSearchResultDTO.builder().id("nav-audit").category("Navigation").title("Security Audit Logs").subtitle("Forensic log trail of all administrative actions").status("Module").link("/superadmin/audit-logs").icon("history").build(),
                com.backend.dto.GlobalSearchResultDTO.builder().id("nav-emergency").category("Navigation").title("Global Emergency Stop").subtitle("Global circuit breaker and kill-switch control").status("Module").link("/superadmin/emergency-stop").icon("warning").build(),
                com.backend.dto.GlobalSearchResultDTO.builder().id("nav-dash").category("Navigation").title("SuperAdmin Dashboard").subtitle("Revenue, onboarding flow, and platform analytics").status("Module").link("/superadmin/dashboard").icon("dashboard").build(),
                com.backend.dto.GlobalSearchResultDTO.builder().id("nav-settings").category("Navigation").title("SuperAdmin System Settings").subtitle("Platform security, maintenance, and notifications").status("Module").link("/superadmin/settings").icon("settings").build()
        );

        for (com.backend.dto.GlobalSearchResultDTO nav : superNav) {
            if (nav.getTitle().toLowerCase().contains(q) || nav.getSubtitle().toLowerCase().contains(q)) {
                results.add(nav);
            }
        }

        return results;
    }
}
