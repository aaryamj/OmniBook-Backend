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

import com.backend.repository.AppointmentRepository;
import com.backend.model.Appointment;
import com.backend.dto.AdminDashboardStatsDTO;
import com.backend.dto.LivePatientFlowDTO;
import com.backend.dto.CRMPatientDTO;
import java.util.Optional;
import com.backend.dto.ProviderStatusDTO;
import java.time.LocalDate;
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

    public void inviteProvider(InviteProviderRequest request, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getTenant() == null) {
            throw new RuntimeException("Your account is not associated with any clinic. You cannot invite providers.");
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("A user with this email already exists");
        }

        // Generate a secure unique token
        String token = UUID.randomUUID().toString();

        Invitation invitation = Invitation.builder()
                .email(request.getEmail())
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
        emailService.sendProviderInvite(request.getEmail(), token, admin.getTenant().getOrganizationName());

        log.info("Provider invitation created for {} in tenant {}", request.getEmail(), admin.getTenant().getOrganizationName());
    }

    public List<ProviderDTO> getAllProviders(String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getTenant() == null) {
            throw new RuntimeException("Admin has no tenant associated");
        }

        List<User> providerUsers = userRepository.findByTenantIdAndRole(admin.getTenant().getId(), "service_provider");

        return providerUsers.stream().map(user -> {
            ProviderProfile profile = providerProfileRepository.findByUser(user).orElse(null);
            
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
            emailService.sendProviderApprovalEmail(provider.getEmail(), admin.getTenant().getOrganizationName());
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
                        .providerName("Dr. " + providerName)
                        .status(a.getAppointmentStatus() != null ? a.getAppointmentStatus() : "SCHEDULED")
                        .billingStatus(billingStatus)
                        .build());
            }
        }

        // Total capacity = providers * 10 just as a dummy logic, or 60.
        int totalCapacity = allProviders.size() > 0 ? allProviders.size() * 15 : 60;

        List<ProviderStatusDTO> providerMatrix = new ArrayList<>();
        for (User p : allProviders) {
            ProviderProfile profile = providerProfileRepository.findByUser(p).orElse(null);
            String status = "ACTIVE";
            if (profile != null && profile.getStatus() != null) {
                status = profile.getStatus().name();
            }
            
            providerMatrix.add(ProviderStatusDTO.builder()
                    .name("Dr. " + p.getFullName())
                    .role(profile != null && profile.getPrimarySpecialty() != null ? profile.getPrimarySpecialty() : "General Physician")
                    .status(status)
                    .profilePictureUrl(p.getProfilePicture())
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

            return CRMPatientDTO.builder()
                    .id("PT-" + Math.abs(key.hashCode() % 1000000))
                    .initials(initials)
                    .bgColor(bgColors[colorIndex])
                    .textColor(textColors[colorIndex])
                    .name(latestApp.getPatientName())
                    .phone(patientUser != null && patientUser.getPhone() != null ? patientUser.getPhone() : latestApp.getPatientPhone())
                    .phoneType("Mobile")
                    .lastVisit(latestApp.getAppointmentDate() != null ? latestApp.getAppointmentDate().toString() : "N/A")
                    .provider("Provider") // Can be enhanced later
                    .balance("Rs. 0")
                    .balanceStatus("Cleared")
                    .status(patientUser != null ? "Active" : "Walk-in")
                    .email(latestApp.getPatientEmail())
                    .age(age)
                    .bloodGroup(patientUser != null ? patientUser.getBloodGroup() : "Unknown")
                    .allergies(patientUser != null ? patientUser.getAllergies() : "None")
                    .patientSince(patientUser != null ? patientUser.getCreatedAt().toLocalDate().toString() : apps.stream().min(java.util.Comparator.comparing(Appointment::getAppointmentDate)).map(a -> a.getAppointmentDate().toString()).orElse("Unknown"))
                    .weight(patientUser != null ? patientUser.getWeight() : "N/A")
                    .heartRate(patientUser != null ? patientUser.getHeartRate() : "N/A")
                    .transactions(java.util.Collections.emptyList())
                    .lifetimeBilledUSD("$0.00")
                    .lifetimeBilledNPR("Rs. 0")
                    .outstandingBalance("Rs. 0")
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
        if ("CHECKED_IN".equals(status)) {
            appointment.setCheckedInAt(java.time.LocalDateTime.now());
        } else if ("COMPLETED".equals(status)) {
            appointment.setCompletedAt(java.time.LocalDateTime.now());
        }
        
        appointmentRepository.save(appointment);
    }

    public void cancelAppointment(Long appointmentId, String adminEmail) {
        updateAppointmentStatus(appointmentId, "CANCELLED", adminEmail);
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
        
        return appointments.stream().map(a -> {
            String initials = "XX";
            if (a.getPatientName() != null && !a.getPatientName().isEmpty()) {
                String[] parts = a.getPatientName().split(" ");
                if (parts.length > 1) {
                    initials = (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
                } else {
                    initials = parts[0].substring(0, 1).toUpperCase();
                }
            }
            
            String providerName = "Unknown";
            User provider = userRepository.findById(a.getProviderId()).orElse(null);
            if (provider != null) {
                providerName = "Dr. " + provider.getFullName();
            }

            return AdminAppointmentDTO.builder()
                    .id(String.valueOf(a.getId()))
                    .patientName(a.getPatientName())
                    .initials(initials)
                    .date(a.getAppointmentDate() != null ? a.getAppointmentDate().toString() : "N/A")
                    .time(a.getAppointmentTime() != null ? a.getAppointmentTime().toString() : "N/A")
                    .providerName(providerName)
                    .department(a.getServiceName() != null ? a.getServiceName() : "General")
                    .status(a.getAppointmentStatus() != null ? a.getAppointmentStatus() : "SCHEDULED")
                    .paymentStatus(a.getPaymentStatus() != null ? a.getPaymentStatus() : "UNPAID")
                    .build();
        }).collect(java.util.stream.Collectors.toList());
    }

    public void rescheduleAppointment(Long appointmentId, String newDate, String newTime, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (!appointment.getTenantId().equals(admin.getTenant().getId())) {
            throw new RuntimeException("Unauthorized");
        }

        appointment.setAppointmentDate(java.time.LocalDate.parse(newDate));
        appointment.setAppointmentTime(java.time.LocalTime.parse(newTime));
        appointment.setAppointmentStatus("SCHEDULED");
        appointmentRepository.save(appointment);
    }

    public void createWalkInAppointment(com.backend.dto.WalkInAppointmentRequest request, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getTenant() == null) {
            throw new RuntimeException("Admin is not associated with any clinic.");
        }

        // Find provider by name
        User provider = userRepository.findAll().stream()
                .filter(u -> "service_provider".equals(u.getRole()) 
                        && u.getTenant() != null 
                        && u.getTenant().getId().equals(admin.getTenant().getId())
                        && ("Dr. " + u.getFullName()).equalsIgnoreCase(request.getProviderName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Provider not found: " + request.getProviderName()));

        Appointment appointment = Appointment.builder()
                .tenantId(admin.getTenant().getId())
                .providerId(provider.getId())
                .patientName(request.getPatientName())
                .patientPhone("0000000000") // Placeholder for walk-in if not provided
                .patientEmail("walkin@example.com")
                .appointmentDate(java.time.LocalDate.parse(request.getDate()))
                .appointmentTime(java.time.LocalTime.parse(request.getTime()))
                .serviceName(request.getDepartment())
                .price(0.0) // Adjust if needed
                .paymentStatus("SUCCESS")
                .paymentMethod("CASH")
                .appointmentStatus("CHECKED_IN") // Usually walk-ins are immediately checked in
                .build();

        appointmentRepository.save(appointment);
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

            transactions.add(com.backend.dto.LedgerTransactionDTO.builder()
                    .id("TXN-" + a.getId())
                    .date(a.getAppointmentDate() != null ? a.getAppointmentDate().format(dateFormatter) : "N/A")
                    .time(a.getAppointmentTime() != null ? a.getAppointmentTime().format(timeFormatter) : "N/A")
                    .patientInitials(initials)
                    .patientName(a.getPatientName())
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
}
