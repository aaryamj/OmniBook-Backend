package com.backend.service;

import com.backend.dto.InviteProviderRequest;
import com.backend.model.Invitation;
import com.backend.model.User;
import com.backend.repository.InvitationRepository;
import com.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.backend.model.ProviderProfile;
import com.backend.model.ProviderStatus;
import com.backend.repository.ProviderProfileRepository;
import com.backend.dto.ProviderDTO;
import com.backend.dto.AdminAppointmentDTO;

import com.backend.model.Tenant;
import com.backend.repository.TenantRepository;
import com.backend.repository.AppointmentRepository;
import com.backend.model.Appointment;
import com.backend.dto.AdminDashboardStatsDTO;
import com.backend.dto.LivePatientFlowDTO;
import com.backend.dto.CRMPatientDTO;
import java.util.Optional;
import com.backend.dto.ProviderStatusDTO;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;
    private final EmailService emailService;
    private final ProviderProfileRepository providerProfileRepository;
    private final AuditLogService auditLogService;
    private final AppointmentRepository appointmentRepository;
    private final TenantRepository tenantRepository;
    private final com.backend.repository.DepartmentRepository departmentRepository;
    private final NotificationService notificationService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.backend.repository.ProviderServiceRepository providerServiceRepository;

    public void inviteProvider(InviteProviderRequest request, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getTenant() == null) {
            throw new RuntimeException("Your account is not associated with any clinic. You cannot invite providers.");
        }

        String tier = admin.getTenant().getSubscriptionTier();
        int maxProviders = -1;
        if (tier != null) {
            String t = tier.toLowerCase();
            if (t.contains("starter")) maxProviders = 5;
            else if (t.contains("pro") || t.contains("professional")) maxProviders = 20;
        }

        if (maxProviders != -1) {
            long currentProviders = userRepository.findByTenantIdAndRole(admin.getTenant().getId(), "service_provider").size();
            long pendingInvites = invitationRepository.countByTenantAndRoleAndUsedFalse(admin.getTenant(), "service_provider");
            
            if (currentProviders + pendingInvites >= maxProviders) {
                throw new RuntimeException("Subscription limit exceeded for the selected plan. Please upgrade to invite more providers.");
            }
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("A user with this email already exists");
        }

        // Generate a secure unique token
        String token = UUID.randomUUID().toString();

        Invitation invitation = Invitation.builder()
                .email(request.getEmail())
                .fullName(request.getName())
                .token(token)
                .role("service_provider")
                .tier(request.getTier())
                .specialization(request.getSpecialization())
                .tenant(admin.getTenant())
                .expiryDate(LocalDateTime.now().plusHours(48))
                .used(false)
                .build();

        invitationRepository.save(invitation);

        // Send email
        emailService.sendProviderInvite(request.getEmail(), token, admin.getTenant().getOrganizationName(), admin.getTenant().getOrganizationType());

        log.info("Provider invitation created for {} in tenant {}", request.getEmail(), admin.getTenant().getOrganizationName());
    }

    public List<ProviderDTO> getAllProviders(String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getTenant() == null) {
            throw new RuntimeException("Admin has no tenant associated");
        }

        List<User> providerUsers = userRepository.findByTenantIdAndRole(admin.getTenant().getId(), "service_provider");
        List<Appointment> tenantAppointments = appointmentRepository.findByTenantId(admin.getTenant().getId());

        LocalDate now = LocalDate.now();
        LocalDate startOfWeek = now.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate endOfWeek = startOfWeek.plusDays(6);

        return providerUsers.stream().map(user -> {
            ProviderProfile profile = providerProfileRepository.findByUser(user).orElse(null);
            
            List<Appointment> providerAppts = tenantAppointments.stream()
                    .filter(a -> a.getProviderId() != null && a.getProviderId().equals(user.getId()))
                    .collect(Collectors.toList());

            double totalEarnings = providerAppts.stream()
                    .filter(a -> "SUCCESS".equalsIgnoreCase(a.getPaymentStatus()) || "PAID".equalsIgnoreCase(a.getPaymentStatus()))
                    .mapToDouble(a -> a.getPrice() != null ? a.getPrice() : 0.0)
                    .sum();

            List<Appointment> thisWeekAppts = providerAppts.stream()
                    .filter(a -> a.getAppointmentDate() != null &&
                            !a.getAppointmentDate().isBefore(startOfWeek) &&
                            !a.getAppointmentDate().isAfter(endOfWeek))
                    .filter(a -> "SUCCESS".equalsIgnoreCase(a.getPaymentStatus()) || "PAID".equalsIgnoreCase(a.getPaymentStatus()))
                    .collect(Collectors.toList());

            double thisWeekStripe = thisWeekAppts.stream()
                    .filter(a -> "STRIPE".equalsIgnoreCase(a.getPaymentMethod()))
                    .mapToDouble(a -> a.getPrice() != null ? a.getPrice() : 0.0)
                    .sum();

            double thisWeekEsewa = thisWeekAppts.stream()
                    .filter(a -> !"STRIPE".equalsIgnoreCase(a.getPaymentMethod()))
                    .mapToDouble(a -> a.getPrice() != null ? a.getPrice() : 0.0)
                    .sum();

            double thisWeekTotal = thisWeekStripe + thisWeekEsewa;

            return ProviderDTO.builder()
                    .id(user.getId())
                    .name(user.getFullName())
                    .email(user.getEmail())
                    .role("provider")
                    .primarySpecialty(profile != null ? profile.getPrimarySpecialty() : "")
                    .tier(profile != null ? profile.getTier() : "TIER 1")
                    .status(profile != null ? profile.getStatus().name() : "PENDING_INVITE")
                    .medicalLicense(profile != null ? profile.getMedicalLicense() : "")
                    .profilePictureUrl(profile != null ? profile.getProfilePictureUrl() : "")
                    .licenseImageUrl(profile != null ? profile.getLicenseImageUrl() : "")
                    .credentials(profile != null ? profile.getCredentials() : "")
                    .thisWeekTotal(thisWeekTotal)
                    .thisWeekStripe(thisWeekStripe)
                    .thisWeekEsewa(thisWeekEsewa)
                    .totalEarnings(totalEarnings)
                    .build();
        }).collect(Collectors.toList());
    }

    public void approveProvider(Long providerId, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        User provider = userRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found"));

        if (!provider.getTenant().getId().equals(admin.getTenant().getId())) {
            throw new RuntimeException("Provider does not belong to your clinic");
        }

        ProviderProfile profile = providerProfileRepository.findByUser(provider)
                .orElseThrow(() -> new RuntimeException("Provider profile not found"));

        if (profile.getStatus() != ProviderStatus.PENDING_APPROVAL) {
            throw new RuntimeException("Only providers pending approval can be approved");
        }

        profile.setStatus(ProviderStatus.ACTIVE);
        providerProfileRepository.save(profile);

        // Send approval email (soft fail if SMTP is down)
        try {
            emailService.sendProviderApprovalEmail(provider.getEmail(), admin.getTenant().getOrganizationName(), admin.getTenant().getOrganizationType());
        } catch (Exception e) {
            log.error("Provider approved, but failed to send email to {}", provider.getEmail(), e);
            // We don't rethrow here so the approval doesn't fail due to an email issue
        }

        log.info("Provider {} approved by Admin {}", provider.getEmail(), adminEmail);
        auditLogService.logAction(admin, "Approved Provider: " + provider.getFullName(), "127.0.0.1");
    }

    public void rejectProvider(Long providerId, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        User provider = userRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found"));

        if (!provider.getTenant().getId().equals(admin.getTenant().getId())) {
            throw new RuntimeException("Provider does not belong to your clinic");
        }

        ProviderProfile profile = providerProfileRepository.findByUser(provider)
                .orElseThrow(() -> new RuntimeException("Provider profile not found"));

        if (profile.getStatus() != ProviderStatus.PENDING_APPROVAL) {
            throw new RuntimeException("Only providers pending approval can be rejected");
        }

        profile.setStatus(ProviderStatus.REJECTED);
        providerProfileRepository.save(profile);

        log.info("Provider {} rejected by Admin {}", provider.getEmail(), adminEmail);
        auditLogService.logAction(admin, "Rejected Provider: " + provider.getFullName(), "127.0.0.1");
    }
    public void suspendProvider(Long providerId, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        User provider = userRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found"));

        if (!provider.getTenant().getId().equals(admin.getTenant().getId())) {
            throw new RuntimeException("Provider does not belong to your clinic");
        }

        ProviderProfile profile = providerProfileRepository.findByUser(provider)
                .orElseThrow(() -> new RuntimeException("Provider profile not found"));

        profile.setStatus(ProviderStatus.SUSPENDED);
        providerProfileRepository.save(profile);
        
        provider.setEnabled(false);
        userRepository.save(provider);

        log.info("Provider {} suspended by Admin {}", provider.getEmail(), adminEmail);
        auditLogService.logAction(admin, "Suspended Provider: " + provider.getFullName(), "127.0.0.1");
    }

    public void reactivateProvider(Long providerId, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        User provider = userRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found"));

        if (!provider.getTenant().getId().equals(admin.getTenant().getId())) {
            throw new RuntimeException("Provider does not belong to your clinic");
        }

        ProviderProfile profile = providerProfileRepository.findByUser(provider)
                .orElseThrow(() -> new RuntimeException("Provider profile not found"));

        profile.setStatus(ProviderStatus.ACTIVE);
        providerProfileRepository.save(profile);
        
        provider.setEnabled(true);
        userRepository.save(provider);

        log.info("Provider {} reactivated by Admin {}", provider.getEmail(), adminEmail);
        auditLogService.logAction(admin, "Reactivated Provider: " + provider.getFullName(), "127.0.0.1");
    }
    public AdminDashboardStatsDTO getDashboardStats(String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        Long tenantId = admin.getTenant().getId();
        if (tenantId == null) {
            throw new RuntimeException("Admin is not associated with any clinic.");
        }

        List<Appointment> allAppointments = appointmentRepository.findByTenantId(tenantId);
        List<User> allProviders = userRepository.findByTenantIdAndRole(tenantId, "service_provider");

        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate endOfWeek = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));

        int todayVolume = 0;
        int activeInClinic = 0;
        int waitingPatients = 0;
        int inConsultPatients = 0;
        double esewaSettled = 0;
        double stripeConnect = 0;

        List<LivePatientFlowDTO> livePatientFlow = new ArrayList<>();
        List<Integer> weeklyAppointments = new ArrayList<>(java.util.Collections.nCopies(7, 0));
        double esewaWeeklyVolume = 0;
        double stripeWeeklyVolume = 0;

        for (Appointment a : allAppointments) {
            LocalDate appDate = a.getAppointmentDate();
            
            // Weekly stats
            if (appDate != null && !appDate.isBefore(startOfWeek) && !appDate.isAfter(endOfWeek)) {
                int dayIndex = appDate.getDayOfWeek().getValue() - 1; // 0=Mon, 6=Sun
                weeklyAppointments.set(dayIndex, weeklyAppointments.get(dayIndex) + 1);

                if ("SUCCESS".equals(a.getPaymentStatus())) {
                    if ("ESEWA".equals(a.getPaymentMethod())) {
                        esewaWeeklyVolume += (a.getPrice() != null ? a.getPrice() : 0);
                    } else if ("STRIPE".equals(a.getPaymentMethod())) {
                        stripeWeeklyVolume += (a.getPrice() != null ? a.getPrice() : 0);
                    }
                }
            }

            // Today stats
            if (appDate != null && appDate.equals(today)) {
                todayVolume++;
                
                if ("CHECKED_IN".equals(a.getAppointmentStatus()) || "WAITING".equals(a.getAppointmentStatus())) {
                    activeInClinic++;
                    waitingPatients++;
                } else if ("IN_CONSULTATION".equals(a.getAppointmentStatus())) {
                    activeInClinic++;
                    inConsultPatients++;
                }

                if ("SUCCESS".equals(a.getPaymentStatus())) {
                    if ("ESEWA".equals(a.getPaymentMethod())) {
                        esewaSettled += (a.getPrice() != null ? a.getPrice() : 0);
                    } else if ("STRIPE".equals(a.getPaymentMethod())) {
                        stripeConnect += (a.getPrice() != null ? a.getPrice() : 0);
                    }
                }

                // Add to live flow
                User provider = userRepository.findById(a.getProviderId()).orElse(null);
                String providerName = provider != null ? provider.getFullName() : "Unknown";
                
                Tenant tenant = admin.getTenant();
                boolean isClinic = tenant != null && "Clinic".equalsIgnoreCase(tenant.getOrganizationType());
                String prefix = (isClinic && !providerName.toLowerCase().startsWith("dr.") ? "Dr. " : "");

                String billingStatus = "Pending";
                if ("SUCCESS".equals(a.getPaymentStatus())) {
                    if ("ESEWA".equals(a.getPaymentMethod())) billingStatus = "[eSewa Verified]";
                    else if ("STRIPE".equals(a.getPaymentMethod())) billingStatus = "[Stripe Verified]";
                    else billingStatus = "[Verified]";
                }
                
                livePatientFlow.add(LivePatientFlowDTO.builder()
                        .id(a.getId())
                        .time(a.getAppointmentTime() != null ? a.getAppointmentTime().toString() : "N/A")
                        .patientName(a.getPatientName())
                        .patientId("ID: " + a.getId())
                        .service(a.getServiceName())
                        .providerName(prefix + providerName)
                        .status(a.getAppointmentStatus() != null ? a.getAppointmentStatus() : "SCHEDULED")
                        .billingStatus(billingStatus)
                        .build());
            }
        }

        // Total capacity = providers * 10 just as a dummy logic, or 60.
        int totalCapacity = allProviders.size() > 0 ? allProviders.size() * 15 : 60;

        List<ProviderStatusDTO> providerMatrix = new ArrayList<>();
        Tenant tenant = admin.getTenant();
        boolean isClinic = tenant != null && "Clinic".equalsIgnoreCase(tenant.getOrganizationType());
        String defaultRole = "Department Faculty";
        if (isClinic) {
            defaultRole = "General Physician";
        } else if (tenant != null && "Saloon".equalsIgnoreCase(tenant.getOrganizationType())) {
            defaultRole = "Senior Stylist";
        } else if (tenant != null && "Other".equalsIgnoreCase(tenant.getOrganizationType())) {
            defaultRole = "Consultant";
        }

        for (User p : allProviders) {
            ProviderProfile profile = providerProfileRepository.findByUser(p).orElse(null);
            String status = "ACTIVE";
            if (profile != null && profile.getStatus() != null) {
                status = profile.getStatus().name();
            }
            
            String pName = p.getFullName() != null ? p.getFullName() : "Unknown";
            String pPrefix = (isClinic && !pName.toLowerCase().startsWith("dr.") ? "Dr. " : "");

            String pPic = (profile != null && profile.getProfilePictureUrl() != null && !profile.getProfilePictureUrl().trim().isEmpty())
                    ? profile.getProfilePictureUrl()
                    : p.getProfilePicture();

            providerMatrix.add(ProviderStatusDTO.builder()
                    .name(pPrefix + pName)
                    .role(profile != null && profile.getPrimarySpecialty() != null ? profile.getPrimarySpecialty() : defaultRole)
                    .status(status)
                    .profilePictureUrl(pPic)
                    .build());
        }

        return AdminDashboardStatsDTO.builder()
                .todayVolume(todayVolume)
                .totalCapacity(totalCapacity)
                .activeInClinic(activeInClinic)
                .waitingPatients(waitingPatients)
                .inConsultPatients(inConsultPatients)
                .esewaSettled(esewaSettled)
                .stripeConnect(stripeConnect)
                .esewaWeeklyVolume(esewaWeeklyVolume)
                .stripeWeeklyVolume(stripeWeeklyVolume)
                .livePatientFlow(livePatientFlow)
                .weeklyAppointments(weeklyAppointments)
                .providerMatrix(providerMatrix)
                .primaryAccentColor(admin.getTenant() != null ? admin.getTenant().getPrimaryAccentColor() : null)
                .organizationName(admin.getTenant() != null ? admin.getTenant().getOrganizationName() : null)
                .organizationType(admin.getTenant() != null ? admin.getTenant().getOrganizationType() : null)
                .build();
    }

    public List<CRMPatientDTO> getAllPatients(String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        List<Appointment> appointments;
        if ("super_admin".equals(admin.getRole())) {
            appointments = appointmentRepository.findAll();
        } else {
            if (admin.getTenant() == null) {
                throw new RuntimeException("Admin is not associated with any clinic.");
            }
            appointments = appointmentRepository.findByTenantId(admin.getTenant().getId());
        }

        // Group appointments by patientEmail (or phone/name if email is null)
        java.util.Map<String, List<Appointment>> patientAppointments = appointments.stream()
                .collect(Collectors.groupingBy(a -> a.getPatientEmail() != null ? a.getPatientEmail() : (a.getPatientPhone() != null ? a.getPatientPhone() : a.getPatientName())));

        return patientAppointments.entrySet().stream().map(entry -> {
            String key = entry.getKey();
            List<Appointment> apps = entry.getValue();
            Appointment latestApp = apps.stream()
                    .max(java.util.Comparator.comparing(Appointment::getAppointmentDate))
                    .orElse(apps.get(0));

            User patientUser = null;
            if (latestApp.getPatientEmail() != null && !latestApp.getPatientEmail().isEmpty()) {
                patientUser = userRepository.findByEmailIgnoringTenant(latestApp.getPatientEmail()).orElse(null);
            }
            if (patientUser == null && latestApp.getPatientPhone() != null && !latestApp.getPatientPhone().isEmpty()) {
                patientUser = userRepository.findByPhoneIgnoringTenant(latestApp.getPatientPhone()).orElse(null);
            }

            String initials = "XX";
            if (latestApp.getPatientName() != null && latestApp.getPatientName().length() > 0) {
                String[] parts = latestApp.getPatientName().trim().split("\\s+");
                if (parts.length > 1) {
                    initials = (parts[0].substring(0,1) + parts[1].substring(0,1)).toUpperCase();
                } else {
                    initials = latestApp.getPatientName().substring(0, Math.min(2, latestApp.getPatientName().length())).toUpperCase();
                }
            }

            Integer age = null;
            if (patientUser != null && patientUser.getDateOfBirth() != null) {
                age = java.time.Period.between(patientUser.getDateOfBirth(), java.time.LocalDate.now()).getYears();
            }

            // Generate deterministic colors based on name
            String[] bgColors = {"bg-primary-fixed", "bg-secondary-fixed", "bg-tertiary-fixed", "bg-error-container"};
            String[] textColors = {"text-on-primary-fixed", "text-on-secondary-fixed", "text-on-tertiary-fixed", "text-on-error-container"};
            int colorIndex = Math.abs(latestApp.getPatientName().hashCode()) % bgColors.length;

            // Compute real timeline items
            List<CRMPatientDTO.CRMAppointmentItemDTO> timelineItems = apps.stream()
                    .sorted(java.util.Comparator.comparing(Appointment::getAppointmentDate).reversed())
                    .map(a -> {
                        String provName = "Provider";
                        if (a.getProviderId() != null) {
                            provName = userRepository.findById(a.getProviderId())
                                    .map(User::getFullName)
                                    .orElse("Provider");
                        }
                        return CRMPatientDTO.CRMAppointmentItemDTO.builder()
                                .id(a.getId())
                                .serviceName(a.getServiceName() != null ? a.getServiceName() : "Service Consultation")
                                .appointmentDate(a.getAppointmentDate() != null ? a.getAppointmentDate().toString() : "N/A")
                                .appointmentTime(a.getAppointmentTime() != null ? a.getAppointmentTime().toString().substring(0, Math.min(5, a.getAppointmentTime().toString().length())) : "")
                                .appointmentStatus(a.getAppointmentStatus())
                                .providerName(provName)
                                .notes(a.getReasonForVisit() != null ? a.getReasonForVisit() : "")
                                .price(a.getPrice() != null ? a.getPrice() : 0.0)
                                .build();
                    }).collect(Collectors.toList());

            double totalBilled = apps.stream()
                    .mapToDouble(a -> a.getPrice() != null ? a.getPrice() : 0.0)
                    .sum();

            String latestProvName = timelineItems.isEmpty() ? "Provider" : timelineItems.get(0).getProviderName();

            return CRMPatientDTO.builder()
                    .id("PT-" + Math.abs(key.hashCode() % 1000000))
                    .initials(initials)
                    .bgColor(bgColors[colorIndex])
                    .textColor(textColors[colorIndex])
                    .name(latestApp.getPatientName())
                    .profilePicture(patientUser != null ? patientUser.getProfilePicture() : null)
                    .phone(patientUser != null && patientUser.getPhone() != null ? patientUser.getPhone() : latestApp.getPatientPhone())
                    .phoneType("Mobile")
                    .lastVisit(latestApp.getAppointmentDate() != null ? latestApp.getAppointmentDate().toString() : "N/A")
                    .provider(latestProvName)
                    .balance("Rs. 0")
                    .balanceStatus("Cleared")
                    .status(patientUser != null ? "Active" : "Walk-in")
                    .email(latestApp.getPatientEmail())
                    .age(age)
                    .bloodGroup(patientUser != null ? patientUser.getBloodGroup() : "Unknown")
                    .allergies(patientUser != null ? patientUser.getAllergies() : "None")
                    .patientSince(patientUser != null && patientUser.getCreatedAt() != null ? patientUser.getCreatedAt().toLocalDate().toString() : apps.stream().min(java.util.Comparator.comparing(Appointment::getAppointmentDate)).map(a -> a.getAppointmentDate().toString()).orElse("Unknown"))
                    .weight(patientUser != null ? patientUser.getWeight() : "N/A")
                    .heartRate(patientUser != null ? patientUser.getHeartRate() : "N/A")
                    .transactions(java.util.Collections.emptyList())
                    .lifetimeBilledUSD(String.format("$%.2f", totalBilled / 135.0))
                    .lifetimeBilledNPR(String.format("Rs. %.0f", totalBilled))
                    .outstandingBalance("Rs. 0")
                    .timeline(timelineItems)
                    .build();
        }).collect(Collectors.toList());
    }

    public void updateAppointmentStatus(Long appointmentId, String status, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getTenant() == null) {
            throw new RuntimeException("Admin is not associated with any clinic.");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (!appointment.getTenantId().equals(admin.getTenant().getId())) {
            throw new RuntimeException("Unauthorized: Appointment belongs to a different clinic");
        }

        appointment.setAppointmentStatus(status);
        if ("SCHEDULED".equals(status) || "APPROVED".equals(status)) {
            appointment.setApprovedAt(java.time.LocalDateTime.now());
            appointment.setApprovedByName(admin.getFullName() != null ? admin.getFullName() : admin.getEmail());
            appointment.setApprovedByRole(admin.getTenantRole() != null ? admin.getTenantRole().getRoleName() : admin.getRole());
            appointment.setApprovedByUserId(admin.getId());
        } else if ("CHECKED_IN".equals(status)) {
            appointment.setCheckedInAt(java.time.LocalDateTime.now());
            appointment.setCheckedInByName(admin.getFullName() != null ? admin.getFullName() : admin.getEmail());
            appointment.setCheckedInByRole(admin.getTenantRole() != null ? admin.getTenantRole().getRoleName() : admin.getRole());
            appointment.setCheckedInByUserId(admin.getId());
        } else if ("COMPLETED".equals(status)) {
            appointment.setCompletedAt(java.time.LocalDateTime.now());
            appointment.setCompletedByName(admin.getFullName() != null ? admin.getFullName() : admin.getEmail());
            appointment.setCompletedByRole(admin.getTenantRole() != null ? admin.getTenantRole().getRoleName() : admin.getRole());
            appointment.setCompletedByUserId(admin.getId());
        } else if ("CANCELLED".equals(status)) {
            appointment.setCancelledAt(java.time.LocalDateTime.now());
            appointment.setCancelledByName(admin.getFullName() != null ? admin.getFullName() : admin.getEmail());
            appointment.setCancelledByRole(admin.getTenantRole() != null ? admin.getTenantRole().getRoleName() : admin.getRole());
            appointment.setCancelledByUserId(admin.getId());
        }
        
        appointmentRepository.save(appointment);

        // Dispatch notifications based on new status
        try {
            Long tenantId = admin.getTenant() != null ? admin.getTenant().getId() : appointment.getTenantId();
            Long patientUserId = appointment.getBookedByUserId();
            if (patientUserId == null && appointment.getPatientEmail() != null) {
                patientUserId = userRepository.findByEmailIgnoringTenant(appointment.getPatientEmail()).map(User::getId).orElse(null);
            }

            if ("SCHEDULED".equals(status) || "APPROVED".equals(status)) {
                notificationService.notifyAppointmentApproved(appointment);
            } else if ("CHECKED_IN".equals(status)) {
                notificationService.notifyAppointmentCheckedIn(appointment);
            } else if ("COMPLETED".equals(status)) {
                notificationService.notifyAppointmentCompleted(appointment);
            } else if ("CANCELLED".equals(status)) {
                notificationService.notifyAppointmentCancelled(appointment);
            }
        } catch (Exception notifEx) {
            log.warn("Failed to dispatch appointment status notification: {}", notifEx.getMessage());
        }
    }

    public void cancelAppointment(Long appointmentId, String adminEmail) {
        updateAppointmentStatus(appointmentId, "CANCELLED", adminEmail);
    }

    private AdminAppointmentDTO mapToAdminAppointmentDTO(Appointment a, User admin) {
        String initials = "XX";
        if (a.getPatientName() != null && !a.getPatientName().isEmpty()) {
            String[] parts = a.getPatientName().trim().split("\\s+");
            if (parts.length > 1) {
                initials = (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
            } else {
                initials = parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
            }
        }
        
        Tenant appTenant = null;
        if (a.getTenantId() != null) {
            appTenant = tenantRepository.findById(a.getTenantId()).orElse(null);
        }
        if (appTenant == null && admin != null) {
            appTenant = admin.getTenant();
        }

        boolean isClinic = appTenant != null && "Clinic".equalsIgnoreCase(appTenant.getOrganizationType());
        
        String providerName = "Unknown";
        String doctorProfilePicture = null;
        String doctorSpecialty = a.getServiceName() != null ? a.getServiceName() : "General";
        if (a.getProviderId() != null) {
            User provider = userRepository.findById(a.getProviderId()).orElse(null);
            if (provider != null) {
                String pFullName = provider.getFullName() != null ? provider.getFullName() : "Unknown";
                String prefix = (isClinic && !pFullName.toLowerCase().startsWith("dr.") ? "Dr. " : "");
                providerName = prefix + pFullName;
                ProviderProfile profile = providerProfileRepository.findByUser(provider).orElse(null);
                if (profile != null && profile.getPrimarySpecialty() != null) {
                    doctorSpecialty = profile.getPrimarySpecialty();
                }
                doctorProfilePicture = (profile != null && profile.getProfilePictureUrl() != null && !profile.getProfilePictureUrl().trim().isEmpty())
                        ? profile.getProfilePictureUrl()
                        : provider.getProfilePicture();
            }
        }

        String patientProfilePic = null;
        if (a.getPatientEmail() != null && !a.getPatientEmail().isEmpty()) {
            User patientUser = userRepository.findByEmailIgnoringTenant(a.getPatientEmail()).orElse(null);
            if (patientUser != null) {
                patientProfilePic = patientUser.getProfilePicture();
            }
        }

        String resolvedOrgType = appTenant != null ? appTenant.getOrganizationType() : "Clinic";
        String customerRole = "client";
        if (resolvedOrgType != null) {
            String ot = resolvedOrgType.toLowerCase();
            if (ot.contains("college") || ot.contains("univ") || ot.contains("school") || ot.contains("educ")) {
                customerRole = "student";
            } else if (ot.contains("clinic") || ot.contains("hosp") || ot.contains("med")) {
                customerRole = "patient";
            }
        }

        // Booked by should always fetch the name and role of the user (not admin, provider, etc.)
        String resolvedBookedByName = (a.getPatientName() != null && !a.getPatientName().trim().isEmpty())
                ? a.getPatientName().trim()
                : (a.getBookedByName() != null && !a.getBookedByName().trim().isEmpty() ? a.getBookedByName().trim() : "User");

        String resolvedBookedByRole = (a.getBookedByRole() != null 
                && !a.getBookedByRole().equalsIgnoreCase("admin") 
                && !a.getBookedByRole().equalsIgnoreCase("service_provider")
                && !a.getBookedByRole().equalsIgnoreCase("provider")
                && !a.getBookedByRole().equalsIgnoreCase("super_admin"))
                ? a.getBookedByRole()
                : customerRole;

        return AdminAppointmentDTO.builder()
                .id(String.valueOf(a.getId()))
                .patientName(a.getPatientName())
                .patientEmail(a.getPatientEmail())
                .patientPhone(a.getPatientPhone())
                .patientProfilePicture(patientProfilePic)
                .initials(initials)
                .date(a.getAppointmentDate() != null ? a.getAppointmentDate().toString() : "N/A")
                .time(a.getAppointmentTime() != null ? a.getAppointmentTime().toString() : "N/A")
                .providerName(providerName)
                .doctorSpecialty(doctorSpecialty)
                .doctorProfilePicture(doctorProfilePicture)
                .department(a.getServiceName() != null ? a.getServiceName() : "General")
                .status(a.getAppointmentStatus() != null ? a.getAppointmentStatus() : "SCHEDULED")
                .paymentStatus(a.getPaymentStatus() != null ? a.getPaymentStatus() : "UNPAID")
                .appointmentType(a.getAppointmentType() != null ? a.getAppointmentType() : "IN_PERSON")
                .meetingLink(a.getMeetingLink())
                .videoCallEnabled(Boolean.TRUE.equals(a.getVideoCallEnabled()) || "VIRTUAL".equalsIgnoreCase(a.getAppointmentType()))
                .serviceAllowsVideo(Boolean.TRUE.equals(a.getServiceAllowsVideo()))
                .price(a.getPrice())
                .reasonForVisit(a.getReasonForVisit())
                .organizationType(resolvedOrgType)
                .organizationName(appTenant != null ? appTenant.getOrganizationName() : "Clinic")
                .bookedAt(a.getBookedAt() != null ? a.getBookedAt().toString() : null)
                .bookedByName(resolvedBookedByName)
                .bookedByRole(resolvedBookedByRole)
                .approvedAt(a.getApprovedAt() != null ? a.getApprovedAt().toString() : null)
                .approvedByName(a.getApprovedByName())
                .approvedByRole(a.getApprovedByRole())
                .checkedInAt(a.getCheckedInAt() != null ? a.getCheckedInAt().toString() : null)
                .checkedInByName(a.getCheckedInByName())
                .checkedInByRole(a.getCheckedInByRole())
                .completedAt(a.getCompletedAt() != null ? a.getCompletedAt().toString() : null)
                .completedByName(a.getCompletedByName())
                .completedByRole(a.getCompletedByRole())
                .cancelledAt(a.getCancelledAt() != null ? a.getCancelledAt().toString() : null)
                .cancelledByName(a.getCancelledByName())
                .cancelledByRole(a.getCancelledByRole())
                .treatmentSummary(a.getTreatmentSummary())
                .internalNotes(a.getInternalNotes())
                .patientRating(a.getPatientRating())
                .patientReview(a.getPatientReview())
                .build();
    }

    public List<AdminAppointmentDTO> getAllAppointments(String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        List<Appointment> appointments;
        if ("super_admin".equals(admin.getRole())) {
            appointments = appointmentRepository.findAll();
        } else {
            if (admin.getTenant() == null) {
                throw new RuntimeException("Admin is not associated with any clinic.");
            }
            appointments = appointmentRepository.findByTenantId(admin.getTenant().getId());
        }
        
        return appointments.stream()
                .map(a -> mapToAdminAppointmentDTO(a, admin))
                .collect(java.util.stream.Collectors.toList());
    }

    public AdminAppointmentDTO getAppointmentDetailsById(Long appointmentId, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        Appointment a = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (!"super_admin".equals(admin.getRole())) {
            if (admin.getTenant() == null || !a.getTenantId().equals(admin.getTenant().getId())) {
                throw new RuntimeException("Unauthorized to view this appointment");
            }
        }

        return mapToAdminAppointmentDTO(a, admin);
    }

    public void rescheduleAppointment(Long appointmentId, String newDate, String newTime, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (!appointment.getTenantId().equals(admin.getTenant().getId())) {
            throw new RuntimeException("Unauthorized");
        }

        LocalDate targetDate = java.time.LocalDate.parse(newDate);
        LocalTime targetTime = java.time.LocalTime.parse(newTime);

        // Find service capacity
        int maxCapacity = 1;
        if (appointment.getServiceName() != null && appointment.getProviderId() != null) {
            User pUser = userRepository.findById(appointment.getProviderId()).orElse(null);
            if (pUser != null) {
                com.backend.model.ProviderProfile pProfile = providerProfileRepository.findByUser(pUser).orElse(null);
                if (pProfile != null) {
                    maxCapacity = providerServiceRepository.findByProviderProfile(pProfile).stream()
                            .filter(s -> s.getServiceName() != null && s.getServiceName().equalsIgnoreCase(appointment.getServiceName()))
                            .map(com.backend.model.ProviderService::getMaxCapacity)
                            .filter(java.util.Objects::nonNull)
                            .findFirst()
                            .orElse(1);
                }
            }
        }
        // Verify patient does not already hold another active appointment at target slot
        boolean alreadyBooked = appointmentRepository.existsActiveAppointmentForUserAtSlot(
                appointment.getBookedByUserId(),
                appointment.getPatientEmail(),
                targetDate,
                targetTime
        );
        if (alreadyBooked && !(targetDate.equals(appointment.getAppointmentDate()) && targetTime.equals(appointment.getAppointmentTime()))) {
            throw new RuntimeException("You already have an appointment booked for this date and time.");
        }

        long activeCount = appointmentRepository.countActiveAppointmentsForSlot(appointment.getProviderId(), targetDate, targetTime);
        if (activeCount >= maxCapacity) {
            throw new RuntimeException("Target time slot (" + targetTime + ") is full (capacity: " + maxCapacity + " seats). Cannot reschedule.");
        }

        appointment.setAppointmentDate(targetDate);
        appointment.setAppointmentTime(targetTime);
        appointment.setAppointmentStatus("SCHEDULED");
        appointmentRepository.save(appointment);

        try {
            notificationService.notifyAppointmentRescheduled(appointment, targetDate, targetTime);
        } catch (Exception ex) {
            log.warn("Failed to dispatch reschedule notification: {}", ex.getMessage());
        }
    }

    public void createWalkInAppointment(com.backend.dto.WalkInAppointmentRequest request, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getTenant() == null) {
            throw new RuntimeException("Admin is not associated with any organization.");
        }

        // 1. Find provider by ID or flexible Name
        User provider = null;
        if (request.getProviderId() != null) {
            provider = userRepository.findById(request.getProviderId())
                    .filter(u -> "service_provider".equals(u.getRole()) 
                            && u.getTenant() != null 
                            && u.getTenant().getId().equals(admin.getTenant().getId()))
                    .orElse(null);
        }

        if (provider == null && "service_provider".equals(admin.getRole())) {
            provider = admin;
        }

        if (provider == null && request.getProviderName() != null && !request.getProviderName().trim().isEmpty()) {
            final String rawName = request.getProviderName().trim();
            final String cleanName = rawName.replaceFirst("^(?i)(Dr\\.|Prof\\.|Mr\\.|Ms\\.|Mrs\\.)\\s*", "").trim();

            provider = userRepository.findAll().stream()
                    .filter(u -> "service_provider".equals(u.getRole()) 
                            && u.getTenant() != null 
                            && u.getTenant().getId().equals(admin.getTenant().getId()))
                    .filter(u -> {
                        String uName = u.getFullName() != null ? u.getFullName().trim() : "";
                        String uClean = uName.replaceFirst("^(?i)(Dr\\.|Prof\\.|Mr\\.|Ms\\.|Mrs\\.)\\s*", "").trim();
                        return uName.equalsIgnoreCase(rawName)
                                || uClean.equalsIgnoreCase(cleanName)
                                || ("Dr. " + uName).equalsIgnoreCase(rawName)
                                || ("Prof. " + uName).equalsIgnoreCase(rawName);
                    })
                    .findFirst()
                    .orElse(null);
        }

        if (provider == null) {
            provider = userRepository.findAll().stream()
                    .filter(u -> "service_provider".equals(u.getRole()) 
                            && u.getTenant() != null 
                            && u.getTenant().getId().equals(admin.getTenant().getId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No active provider found in this organization."));
        }

        // 2. Student / Customer details
        String patientName = (request.getPatientName() != null && !request.getPatientName().trim().isEmpty())
                ? request.getPatientName().trim()
                : "Walk-in Guest";
        String patientEmail = (request.getPatientEmail() != null && !request.getPatientEmail().trim().isEmpty())
                ? request.getPatientEmail().trim()
                : null;
        String patientPhone = (request.getPatientPhone() != null && !request.getPatientPhone().trim().isEmpty())
                ? request.getPatientPhone().trim()
                : "N/A";

        // Determine customer role based on organization type
        String customerRole = "client";
        if (admin.getTenant() != null && admin.getTenant().getOrganizationType() != null) {
            String ot = admin.getTenant().getOrganizationType().toLowerCase();
            if (ot.contains("college") || ot.contains("univ") || ot.contains("school") || ot.contains("educ")) {
                customerRole = "student";
            } else if (ot.contains("clinic") || ot.contains("hosp") || ot.contains("med")) {
                customerRole = "patient";
            }
        }

        Long bookedUserId = null;
        // Check or create user record for student/patient if email provided
        if (patientEmail != null) {
            java.util.Optional<User> existingUser = userRepository.findByEmailIgnoringTenant(patientEmail);
            if (existingUser.isEmpty()) {
                String cleanPhone = (patientPhone == null || patientPhone.equals("N/A") || patientPhone.trim().isEmpty()) ? null : patientPhone.trim();
                if (cleanPhone != null && userRepository.findByPhoneIgnoringTenant(cleanPhone).isPresent()) {
                    cleanPhone = null;
                }

                User newUser = User.builder()
                        .fullName(patientName)
                        .email(patientEmail)
                        .phone(cleanPhone)
                        .password(passwordEncoder.encode("Welcome@123"))
                        .role("user")
                        .enabled(true)
                        .tenant(admin.getTenant())
                        .build();
                User saved = userRepository.save(newUser);
                bookedUserId = saved.getId();
            } else {
                bookedUserId = existingUser.get().getId();
            }
        }

        // 3. Service and Pricing
        String serviceName = request.getServiceName() != null && !request.getServiceName().trim().isEmpty()
                ? request.getServiceName().trim()
                : (request.getDepartment() != null && !request.getDepartment().trim().isEmpty() ? request.getDepartment().trim() : "General Session");

        Double price = request.getPrice() != null ? request.getPrice() : 0.0;
        String paymentStatus = request.getPaymentStatus() != null && !request.getPaymentStatus().trim().isEmpty()
                ? request.getPaymentStatus().trim().toUpperCase()
                : "SUCCESS";
        String paymentMethod = request.getPaymentMethod() != null && !request.getPaymentMethod().trim().isEmpty()
                ? request.getPaymentMethod().trim().toUpperCase()
                : "CASH";
        String appointmentStatus = request.getAppointmentStatus() != null && !request.getAppointmentStatus().trim().isEmpty()
                ? request.getAppointmentStatus().trim().toUpperCase()
                : "CHECKED_IN";

        LocalDate appDate = (request.getDate() != null && !request.getDate().trim().isEmpty())
                ? LocalDate.parse(request.getDate().trim())
                : LocalDate.now();

        LocalTime appTime = (request.getTime() != null && !request.getTime().trim().isEmpty())
                ? LocalTime.parse(request.getTime().trim())
                : LocalTime.now().withSecond(0).withNano(0);

        LocalDateTime now = LocalDateTime.now();
        String transactionId = "TXN-" + java.util.UUID.randomUUID().toString();

        Appointment.AppointmentBuilder appBuilder = Appointment.builder()
                .tenantId(admin.getTenant().getId())
                .providerId(provider.getId())
                .patientName(patientName)
                .patientPhone(patientPhone)
                .patientEmail(patientEmail != null ? patientEmail : "walkin_" + System.currentTimeMillis() + "@tenant.local")
                .appointmentDate(appDate)
                .appointmentTime(appTime)
                .appointmentType("IN_PERSON")
                .serviceName(serviceName)
                .price(price)
                .paymentStatus(paymentStatus)
                .paymentMethod(paymentMethod)
                .appointmentStatus(appointmentStatus)
                .transactionId(transactionId)
                .reasonForVisit(request.getReasonForVisit())
                .internalNotes(request.getInternalNotes())
                .bookedAt(now)
                .bookedByName(patientName)
                .bookedByRole(customerRole)
                .bookedByUserId(bookedUserId);

        if ("CHECKED_IN".equals(appointmentStatus) || "IN_CONSULTATION".equals(appointmentStatus) || "COMPLETED".equals(appointmentStatus)) {
            appBuilder.checkedInAt(now)
                    .checkedInByName(admin.getFullName())
                    .checkedInByRole(admin.getRole())
                    .checkedInByUserId(admin.getId());
        }

        if ("COMPLETED".equals(appointmentStatus)) {
            appBuilder.completedAt(now)
                    .completedByName(admin.getFullName())
                    .completedByRole(admin.getRole())
                    .completedByUserId(admin.getId());
        }

        // Capacity check for walk-in appointment
        int maxCapacity = 1;
        if (serviceName != null && provider != null) {
            com.backend.model.ProviderProfile pProfile = providerProfileRepository.findByUser(provider).orElse(null);
            if (pProfile != null) {
                maxCapacity = providerServiceRepository.findByProviderProfile(pProfile).stream()
                        .filter(s -> s.getServiceName() != null && s.getServiceName().equalsIgnoreCase(serviceName))
                        .map(com.backend.model.ProviderService::getMaxCapacity)
                        .filter(java.util.Objects::nonNull)
                        .findFirst()
                        .orElse(1);
            }
        }
        final int allowedCapacity = Math.max(1, maxCapacity);
        String slotLockKey = ("SLOT_CAPACITY_" + provider.getId() + "_" + appDate + "_" + appTime).intern();
        Appointment savedAppointment;
        synchronized (slotLockKey) {
            // Check if patient already has an active appointment at this date and time
            boolean alreadyBooked = appointmentRepository.existsActiveAppointmentForUserAtSlot(
                    null,
                    request.getPatientEmail(),
                    appDate,
                    appTime
            );
            if (alreadyBooked) {
                throw new RuntimeException("You already have an appointment booked for this date and time.");
            }

            long activeBookings = appointmentRepository.countActiveAppointmentsForSlot(provider.getId(), appDate, appTime);
            if (activeBookings >= allowedCapacity) {
                throw new RuntimeException("This time slot (" + appTime + ") has reached its maximum capacity (" + allowedCapacity + " seats). Cannot add walk-in.");
            }
            savedAppointment = appointmentRepository.save(appBuilder.build());
        }

        // Dispatch notifications for new walk-in / created appointment
        try {
            notificationService.notifyBookingConfirmed(savedAppointment);
        } catch (Exception notifEx) {
            log.warn("Failed to dispatch walk-in notification: {}", notifEx.getMessage());
        }

        // Audit log action
        auditLogService.logAction(admin, 
                "Created Walk-In " + (admin.getTenant() != null ? admin.getTenant().getOrganizationType() : "Session") + 
                " for " + savedAppointment.getPatientName() + " (ID: #" + savedAppointment.getId() + ")", 
                "127.0.0.1");
    }

    public com.backend.dto.LedgerReconciliationDTO getLedgerReconciliation(String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        List<Appointment> appointments;
        if ("super_admin".equals(admin.getRole())) {
            appointments = appointmentRepository.findAll();
        } else {
            if (admin.getTenant() == null) {
                throw new RuntimeException("Admin is not associated with any clinic.");
            }
            appointments = appointmentRepository.findByTenantId(admin.getTenant().getId());
        }

        double grossVolumeUSD = 0.0;
        double grossVolumeNPR = 0.0;
        double stripeEscrow = 0.0;
        double esewaSettled = 0.0;
        double platformFeesUSD = 0.0;
        double platformFeesNPR = 0.0;
        
        java.time.LocalDate today = java.time.LocalDate.now();
        java.util.Map<java.time.LocalDate, Double> stripeDaily = new java.util.HashMap<>();
        java.util.Map<java.time.LocalDate, Double> esewaDaily = new java.util.HashMap<>();
        
        for (int i = 6; i >= 0; i--) {
            stripeDaily.put(today.minusDays(i), 0.0);
            esewaDaily.put(today.minusDays(i), 0.0);
        }

        List<com.backend.dto.LedgerTransactionDTO> transactions = new java.util.ArrayList<>();

        java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy");
        java.time.format.DateTimeFormatter timeFormatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a");

        for (Appointment a : appointments) {
            boolean isStripe = "STRIPE".equalsIgnoreCase(a.getPaymentMethod());
            boolean isEsewa = "ESEWA".equalsIgnoreCase(a.getPaymentMethod());
            double price = a.getPrice() != null ? a.getPrice() : 0.0;
            
            // Calculate KPIs
            if ("SUCCESS".equalsIgnoreCase(a.getPaymentStatus()) || "SETTLED".equalsIgnoreCase(a.getPaymentStatus())) {
                if (isStripe) {
                    grossVolumeUSD += price;
                    platformFeesUSD += price * 0.02;
                } else if (isEsewa) {
                    grossVolumeNPR += price;
                    esewaSettled += price;
                    platformFeesNPR += price * 0.02;
                }
            } else if ("PENDING".equalsIgnoreCase(a.getPaymentStatus()) || "IN ESCROW".equalsIgnoreCase(a.getPaymentStatus())) {
                if (isStripe) {
                    stripeEscrow += price;
                }
            }
            
            // Calculate Chart Data
            if (a.getAppointmentDate() != null) {
                java.time.LocalDate apptDate = a.getAppointmentDate();
                if (!apptDate.isBefore(today.minusDays(6)) && !apptDate.isAfter(today)) {
                    if (isStripe && ("SUCCESS".equalsIgnoreCase(a.getPaymentStatus()) || "PENDING".equalsIgnoreCase(a.getPaymentStatus()))) {
                        stripeDaily.put(apptDate, stripeDaily.getOrDefault(apptDate, 0.0) + price);
                    } else if (isEsewa && "SUCCESS".equalsIgnoreCase(a.getPaymentStatus())) {
                        esewaDaily.put(apptDate, esewaDaily.getOrDefault(apptDate, 0.0) + price);
                    }
                }
            }
            
            // Map Transaction DTO
            String status = a.getPaymentStatus();
            String statusColor = "text-on-surface-variant bg-surface-container-low border-outline-variant";
            String statusDot = "bg-outline";
            if ("SUCCESS".equalsIgnoreCase(status) || "SETTLED".equalsIgnoreCase(status)) {
                status = "Settled";
                statusColor = "text-green-600 bg-green-50 border-green-100";
                statusDot = "bg-green-500";
            } else if ("PENDING".equalsIgnoreCase(status) || "IN ESCROW".equalsIgnoreCase(status)) {
                status = "In Escrow";
                statusColor = "text-amber-600 bg-amber-50 border-amber-100";
                statusDot = "bg-amber-500";
            } else if ("FAILED".equalsIgnoreCase(status) || "REFUNDED".equalsIgnoreCase(status)) {
                status = "Refunded";
                statusColor = "text-on-surface-variant bg-surface-container-low border-outline-variant";
                statusDot = "bg-outline";
            }

            String initials = "XX";
            if (a.getPatientName() != null && a.getPatientName().length() > 0) {
                String[] parts = a.getPatientName().trim().split("\\s+");
                if (parts.length > 1) {
                    initials = (parts[0].substring(0,1) + parts[1].substring(0,1)).toUpperCase();
                } else {
                    initials = a.getPatientName().substring(0, Math.min(2, a.getPatientName().length())).toUpperCase();
                }
            }

            // Generate deterministic colors based on name
            String[] bgColors = {"bg-primary-container text-white", "bg-secondary text-primary-container", "bg-surface-container text-primary", "bg-error-container text-white"};
            int colorIndex = Math.abs(a.getPatientName() != null ? a.getPatientName().hashCode() : 0) % bgColors.length;

            String patientProfilePic = null;
            if (a.getPatientEmail() != null && !a.getPatientEmail().isEmpty()) {
                User patientUser = userRepository.findByEmailIgnoringTenant(a.getPatientEmail()).orElse(null);
                if (patientUser != null) {
                    patientProfilePic = patientUser.getProfilePicture();
                }
            }

            transactions.add(com.backend.dto.LedgerTransactionDTO.builder()
                    .id("TXN-" + a.getId())
                    .date(a.getAppointmentDate() != null ? a.getAppointmentDate().format(dateFormatter) : "N/A")
                    .time(a.getAppointmentTime() != null ? a.getAppointmentTime().format(timeFormatter) : "N/A")
                    .patientInitials(initials)
                    .patientName(a.getPatientName())
                    .patientProfilePicture(patientProfilePic)
                    .service(a.getServiceName())
                    .patientColor(bgColors[colorIndex])
                    .gateway(isStripe ? "Stripe" : (isEsewa ? "eSewa" : (a.getPaymentMethod() != null ? a.getPaymentMethod() : "CASH")))
                    .gatewayColor(isStripe ? "bg-primary text-white" : (isEsewa ? "bg-green-600 text-white" : "bg-gray-600 text-white"))
                    .amount((isStripe ? "$" : "Rs. ") + price)
                    .accountType(isStripe ? "USD Account" : "NPR Wallet")
                    .status(status)
                    .statusColor(statusColor)
                    .statusDot(statusDot)
                    .build());
        }

        // Sort transactions by ID desc
        transactions.sort((t1, t2) -> t2.getId().compareTo(t1.getId()));

        List<com.backend.dto.LedgerChartDataDTO> chartDataList = new java.util.ArrayList<>();
        java.time.format.DateTimeFormatter chartDateFmt = java.time.format.DateTimeFormatter.ofPattern("MMM dd");
        
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate d = today.minusDays(i);
            chartDataList.add(com.backend.dto.LedgerChartDataDTO.builder()
                    .date(d.format(chartDateFmt).toUpperCase())
                    .stripe(stripeDaily.get(d))
                    .esewa(esewaDaily.get(d))
                    .peak(false)
                    .build());
        }
        
        if (!chartDataList.isEmpty()) {
            com.backend.dto.LedgerChartDataDTO peakDay = chartDataList.get(0);
            double maxTotal = 0.0;
            for (com.backend.dto.LedgerChartDataDTO c : chartDataList) {
                double total = c.getStripe() + c.getEsewa();
                if (total > maxTotal) {
                    maxTotal = total;
                    peakDay = c;
                }
            }
            if (maxTotal > 0) peakDay.setPeak(true);
        }

        return com.backend.dto.LedgerReconciliationDTO.builder()
                .grossVolumeUSD(grossVolumeUSD)
                .grossVolumeNPR(grossVolumeNPR)
                .stripeEscrow(stripeEscrow)
                .esewaSettled(esewaSettled)
                .platformFeesUSD(platformFeesUSD)
                .platformFeesNPR(platformFeesNPR)
                .chartData(chartDataList)
                .transactions(transactions)
                .build();
    }

    public void runDailySettlement(String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        List<Appointment> appointments;
        if ("super_admin".equals(admin.getRole())) {
            appointments = appointmentRepository.findAll();
        } else {
            if (admin.getTenant() == null) {
                throw new RuntimeException("Admin is not associated with any clinic.");
            }
            appointments = appointmentRepository.findByTenantId(admin.getTenant().getId());
        }

        for (Appointment a : appointments) {
            if ("PENDING".equalsIgnoreCase(a.getPaymentStatus()) || "IN ESCROW".equalsIgnoreCase(a.getPaymentStatus())) {
                a.setPaymentStatus("SUCCESS");
                appointmentRepository.save(a);
            }
        }
    }

    public List<com.backend.dto.GlobalSearchResultDTO> globalSearch(String adminEmail, String query) {
        if (query == null || query.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        String q = query.trim().toLowerCase();

        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getTenant() == null) {
            throw new RuntimeException("Admin is not associated with any organization.");
        }

        Long tenantId = admin.getTenant().getId();
        Tenant tenant = admin.getTenant();
        boolean isCollege = tenant.getOrganizationType() != null && 
                (tenant.getOrganizationType().toLowerCase().contains("college") || 
                 tenant.getOrganizationType().toLowerCase().contains("univ") ||
                 tenant.getOrganizationType().toLowerCase().contains("school") ||
                 tenant.getOrganizationType().toLowerCase().contains("educ"));
        String customerSingular = isCollege ? "Student" : "Patient";
        String customerPlural = isCollege ? "Students" : "Patients";
        String providerSingular = isCollege ? "Faculty" : "Doctor / Provider";

        List<com.backend.dto.GlobalSearchResultDTO> results = new ArrayList<>();

        // 1. Search Appointments in this tenant
        List<Appointment> tenantAppts = appointmentRepository.findByTenantIdOrderByAppointmentDateDesc(tenantId);
        int apptCount = 0;
        for (Appointment a : tenantAppts) {
            if (apptCount >= 10) break;
            boolean matches = (a.getPatientName() != null && a.getPatientName().toLowerCase().contains(q)) ||
                              (a.getPatientEmail() != null && a.getPatientEmail().toLowerCase().contains(q)) ||
                              (a.getPatientPhone() != null && a.getPatientPhone().toLowerCase().contains(q)) ||
                              (a.getTransactionId() != null && a.getTransactionId().toLowerCase().contains(q)) ||
                              (a.getServiceName() != null && a.getServiceName().toLowerCase().contains(q)) ||
                              (a.getReasonForVisit() != null && a.getReasonForVisit().toLowerCase().contains(q)) ||
                              String.valueOf(a.getId()).equals(q) ||
                              ("APPT-" + a.getId()).toLowerCase().contains(q);
            if (matches) {
                String sub = (a.getAppointmentDate() != null ? a.getAppointmentDate().toString() : "") +
                             (a.getAppointmentTime() != null ? " " + a.getAppointmentTime().toString() : "") +
                             (a.getServiceName() != null ? " • " + a.getServiceName() : "");
                results.add(com.backend.dto.GlobalSearchResultDTO.builder()
                        .id("appt-" + a.getId())
                        .category(customerSingular + " Appointments")
                        .title(a.getPatientName() != null ? a.getPatientName() : "Appointment #" + a.getId())
                        .subtitle(sub.trim())
                        .status(a.getAppointmentStatus() != null ? a.getAppointmentStatus() : "SCHEDULED")
                        .link("/admin/appointments?search=" + java.net.URLEncoder.encode(String.valueOf(a.getId()), java.nio.charset.StandardCharsets.UTF_8))
                        .icon("calendar_month")
                        .build());
                apptCount++;
            }
        }

        // 2. Search Patients / Students / Clients in this tenant
        Map<String, Boolean> seenCustomers = new HashMap<>();
        List<User> tenantUsers = userRepository.findByTenantIdAndRole(tenantId, "user");
        for (User u : tenantUsers) {
            if (seenCustomers.size() >= 8) break;
            boolean matches = (u.getFullName() != null && u.getFullName().toLowerCase().contains(q)) ||
                              (u.getEmail() != null && u.getEmail().toLowerCase().contains(q)) ||
                              (u.getPhone() != null && u.getPhone().toLowerCase().contains(q));
            if (matches) {
                seenCustomers.put(u.getEmail(), true);
                String sub = u.getEmail() + (u.getPhone() != null ? " • " + u.getPhone() : "");
                results.add(com.backend.dto.GlobalSearchResultDTO.builder()
                        .id("user-" + u.getId())
                        .category(customerPlural)
                        .title(u.getFullName() != null ? u.getFullName() : u.getEmail())
                        .subtitle(sub)
                        .status(u.isEnabled() ? "Active" : "Inactive")
                        .link("/admin/crm?search=" + java.net.URLEncoder.encode(u.getFullName() != null ? u.getFullName() : u.getEmail(), java.nio.charset.StandardCharsets.UTF_8))
                        .icon("person")
                        .build());
            }
        }

        // Also check distinct walk-ins from appointments if not already seen
        for (Appointment a : tenantAppts) {
            if (seenCustomers.size() >= 12) break;
            if (a.getPatientEmail() != null && !seenCustomers.containsKey(a.getPatientEmail())) {
                boolean matches = (a.getPatientName() != null && a.getPatientName().toLowerCase().contains(q)) ||
                                  (a.getPatientEmail() != null && a.getPatientEmail().toLowerCase().contains(q)) ||
                                  (a.getPatientPhone() != null && a.getPatientPhone().toLowerCase().contains(q));
                if (matches) {
                    seenCustomers.put(a.getPatientEmail(), true);
                    String sub = a.getPatientEmail() + (a.getPatientPhone() != null && !a.getPatientPhone().equals("N/A") ? " • " + a.getPatientPhone() : "");
                    results.add(com.backend.dto.GlobalSearchResultDTO.builder()
                        .id("guest-" + Math.abs(a.getPatientEmail().hashCode()))
                        .category(customerPlural)
                        .title(a.getPatientName() != null ? a.getPatientName() : "Walk-in Guest")
                        .subtitle(sub)
                        .status("Registered")
                        .link("/admin/crm?search=" + java.net.URLEncoder.encode(a.getPatientName() != null ? a.getPatientName() : a.getPatientEmail(), java.nio.charset.StandardCharsets.UTF_8))
                        .icon("person_outline")
                        .build());
                }
            }
        }

        // 3. Search Providers & Staff in this tenant
        List<User> providers = new ArrayList<>(userRepository.findByTenantIdAndRole(tenantId, "service_provider"));
        providers.addAll(userRepository.findByTenantIdAndRole(tenantId, "provider"));
        for (User p : providers) {
            boolean matches = (p.getFullName() != null && p.getFullName().toLowerCase().contains(q)) ||
                              (p.getEmail() != null && p.getEmail().toLowerCase().contains(q)) ||
                              (p.getPhone() != null && p.getPhone().toLowerCase().contains(q));
            if (matches) {
                String roleName = p.getTenantRole() != null ? p.getTenantRole().getRoleName() : providerSingular;
                results.add(com.backend.dto.GlobalSearchResultDTO.builder()
                        .id("prov-" + p.getId())
                        .category("Providers & Staff")
                        .title(p.getFullName() != null ? p.getFullName() : p.getEmail())
                        .subtitle(roleName + " • " + p.getEmail())
                        .status(p.isEnabled() ? "Active" : "Disabled")
                        .link("/admin/providers")
                        .icon("medical_services")
                        .build());
            }
        }

        // 4. Search Departments in this tenant
        if (departmentRepository != null) {
            List<com.backend.model.Department> depts = departmentRepository.findByTenantId(tenantId);
            for (com.backend.model.Department d : depts) {
                boolean matches = (d.getName() != null && d.getName().toLowerCase().contains(q)) ||
                                  (d.getCode() != null && d.getCode().toLowerCase().contains(q)) ||
                                  (d.getDescription() != null && d.getDescription().toLowerCase().contains(q));
                if (matches) {
                    results.add(com.backend.dto.GlobalSearchResultDTO.builder()
                            .id("dept-" + d.getId())
                            .category("Departments")
                            .title(d.getName())
                            .subtitle("Code: " + (d.getCode() != null ? d.getCode() : "N/A"))
                            .status(d.isActive() ? "Active" : "Inactive")
                            .link("/admin/settings")
                            .icon("domain")
                            .build());
                }
            }
        }

        // 5. Admin Navigation shortcuts
        List<com.backend.dto.GlobalSearchResultDTO> navShortcuts = List.of(
                com.backend.dto.GlobalSearchResultDTO.builder().id("nav-all-appts").category("Navigation").title("All Appointments").subtitle("Manage scheduled and walk-in sessions").status("Page").link("/admin/appointments").icon("event").build(),
                com.backend.dto.GlobalSearchResultDTO.builder().id("nav-crm").category("Navigation").title(customerSingular + " CRM & Directory").subtitle("View profiles, booking histories, and contact info").status("Page").link("/admin/crm").icon("contacts").build(),
                com.backend.dto.GlobalSearchResultDTO.builder().id("nav-ledger").category("Navigation").title("Financial Ledger & Reconciliation").subtitle("Dual-ledger clearing and payments").status("Page").link("/admin/ledger").icon("account_balance_wallet").build(),
                com.backend.dto.GlobalSearchResultDTO.builder().id("nav-providers").category("Navigation").title("Manage Providers & Staff").subtitle("Roster, invites, and service approvals").status("Page").link("/admin/providers").icon("badge").build(),
                com.backend.dto.GlobalSearchResultDTO.builder().id("nav-settings").category("Navigation").title("Organization Settings").subtitle("Departments, operating hours, and profile").status("Page").link("/admin/settings").icon("settings").build(),
                com.backend.dto.GlobalSearchResultDTO.builder().id("nav-dash").category("Navigation").title("Admin Dashboard").subtitle("Volume, capacity, and live flow tracker").status("Page").link("/admin/dashboard").icon("dashboard").build()
        );

        for (com.backend.dto.GlobalSearchResultDTO nav : navShortcuts) {
            if (nav.getTitle().toLowerCase().contains(q) || nav.getSubtitle().toLowerCase().contains(q)) {
                results.add(nav);
            }
        }

        return results;
    }
}
