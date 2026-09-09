package com.backend.service;

import com.backend.model.Appointment;
import com.backend.model.Notification;
import com.backend.model.Tenant;
import com.backend.model.User;
import com.backend.repository.NotificationRepository;
import com.backend.repository.TenantRepository;
import com.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    public static class DynamicTerms {
        public final String orgType;
        public final String customer;    // Student, Client, Patient
        public final String provider;    // Instructor, Stylist, Doctor / Specialist
        public final String session;     // Session, Appointment, Booking
        public final String lounge;      // campus / session waiting room, salon lounge, consultation area
        public final String videoTitle;  // Virtual Class, Virtual Consultation, Video Consultation

        public DynamicTerms(String orgType) {
            this.orgType = orgType != null ? orgType : "Organization";
            String clean = this.orgType.trim().toLowerCase();
            if (clean.contains("college") || clean.contains("univ") || clean.contains("acad") || clean.contains("school") || clean.contains("education")) {
                this.customer = "Student";
                this.provider = "Instructor";
                this.session = "Session";
                this.lounge = "your campus / session waiting room";
                this.videoTitle = "Virtual Class";
            } else if (clean.contains("salon") || clean.contains("saloon") || clean.contains("spa") || clean.contains("beauty") || clean.contains("barber")) {
                this.customer = "Client";
                this.provider = "Stylist";
                this.session = "Appointment";
                this.lounge = "the salon lounge";
                this.videoTitle = "Virtual Consultation";
            } else if (clean.contains("clinic") || clean.contains("hospital") || clean.contains("health") || clean.contains("dental") || clean.contains("medical")) {
                this.customer = "Patient";
                this.provider = "Doctor";
                this.session = "Appointment";
                this.lounge = "the consultation area";
                this.videoTitle = "Video Consultation";
            } else {
                this.customer = "Client";
                this.provider = "Specialist";
                this.session = "Booking";
                this.lounge = "the designated waiting area";
                this.videoTitle = "Virtual Session";
            }
        }
    }

    public DynamicTerms getTermsForTenant(Long tenantId) {
        if (tenantId == null) return new DynamicTerms("Organization");
        String orgType = tenantRepository.findById(tenantId)
                .map(Tenant::getOrganizationType)
                .orElse("Organization");
        return new DynamicTerms(orgType);
    }

    public DynamicTerms getTermsForAppointment(Appointment appt) {
        if (appt == null) return new DynamicTerms("Organization");
        if (appt.getOrganizationType() != null && !appt.getOrganizationType().isBlank()) {
            return new DynamicTerms(appt.getOrganizationType());
        }
        return getTermsForTenant(appt.getTenantId());
    }

    @Transactional
    public Notification createNotification(Long userId, String targetRole, Long tenantId, String title, String message, String type, String link) {
        try {
            Notification notification = Notification.builder()
                    .userId(userId)
                    .targetRole(targetRole)
                    .tenantId(tenantId)
                    .title(title)
                    .message(message)
                    .type(type)
                    .link(link)
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            return notificationRepository.save(notification);
        } catch (Exception e) {
            log.error("Failed to create notification: {}", e.getMessage());
            return null;
        }
    }

    // Backwards-compatible legacy signature
    @Transactional
    public void createNotification(String title, String message, String type) {
        createNotification(null, "SUPER_ADMIN", null, title, message, type, null);
    }

    // Role-specific notification helpers
    @Transactional
    public void notifySuperAdmin(String title, String message, String type, String link) {
        createNotification(null, "SUPER_ADMIN", null, title, message, type, link);
    }

    @Transactional
    public void notifyTenantAdmin(Long tenantId, String title, String message, String type, String link) {
        createNotification(null, "ADMIN", tenantId, title, message, type, link);
    }

    @Transactional
    public void notifyProvider(Long providerId, Long tenantId, String title, String message, String type, String link) {
        createNotification(providerId, "SERVICE_PROVIDER", tenantId, title, message, type, link);
    }

    @Transactional
    public void notifyUser(Long userId, String title, String message, String type, String link) {
        createNotification(userId, "USER", null, title, message, type, link);
    }

    // Dynamic Multi-Type Organization Lifecycle Notification Helpers
    @Transactional
    public void notifyBookingConfirmed(Appointment appointment) {
        if (appointment == null) return;
        DynamicTerms terms = getTermsForAppointment(appointment);
        Long tenantId = appointment.getTenantId();
        Long patientUserId = resolvePatientUserId(appointment);

        if (patientUserId != null) {
            notifyUser(patientUserId,
                    terms.session + " Confirmed",
                    "Your " + terms.session.toLowerCase() + " for " + appointment.getServiceName() + " on " + appointment.getAppointmentDate() + " at " + appointment.getAppointmentTime() + " is confirmed.",
                    "BOOKING_CONFIRMED",
                    "/my-appointments");
        }

        if (appointment.getProviderId() != null) {
            notifyProvider(appointment.getProviderId(), tenantId,
                    "New " + terms.session,
                    "New " + terms.session.toLowerCase() + " booked with " + terms.customer.toLowerCase() + " " + appointment.getPatientName() + " on " + appointment.getAppointmentDate() + " at " + appointment.getAppointmentTime() + ".",
                    "BOOKING_CONFIRMED",
                    "/provider-dashboard");
        }

        if (tenantId != null) {
            notifyTenantAdmin(tenantId,
                    "New " + terms.session + " Booked",
                    appointment.getPatientName() + " (" + terms.customer + ") booked " + appointment.getServiceName() + " on " + appointment.getAppointmentDate() + ".",
                    "NEW_APPOINTMENT",
                    "/admin/appointments?search=" + appointment.getId());
        }
    }

    @Transactional
    public void notifyAppointmentApproved(Appointment appointment) {
        if (appointment == null) return;
        DynamicTerms terms = getTermsForAppointment(appointment);
        Long tenantId = appointment.getTenantId();
        Long patientUserId = resolvePatientUserId(appointment);

        if (patientUserId != null) {
            notifyUser(patientUserId,
                    terms.session + " Approved",
                    "Your " + terms.session.toLowerCase() + " for " + appointment.getServiceName() + " on " + appointment.getAppointmentDate() + " at " + appointment.getAppointmentTime() + " has been approved.",
                    "APPROVED",
                    "/my-appointments");
        }

        if (appointment.getProviderId() != null) {
            notifyProvider(appointment.getProviderId(), tenantId,
                    terms.session + " Approved",
                    terms.session + " with " + terms.customer.toLowerCase() + " " + appointment.getPatientName() + " on " + appointment.getAppointmentDate() + " is approved.",
                    "APPROVED",
                    "/master-calendar");
        }

        if (tenantId != null) {
            notifyTenantAdmin(tenantId,
                    terms.session + " Approved",
                    terms.session + " #" + appointment.getId() + " for " + appointment.getPatientName() + " (" + terms.customer + ") has been approved.",
                    "APPROVED",
                    "/admin/appointments?search=" + appointment.getId());
        }
    }

    @Transactional
    public void notifyAppointmentCheckedIn(Appointment appointment) {
        if (appointment == null) return;
        DynamicTerms terms = getTermsForAppointment(appointment);
        Long tenantId = appointment.getTenantId();
        Long patientUserId = resolvePatientUserId(appointment);

        if (patientUserId != null) {
            notifyUser(patientUserId,
                    "Checked In Successfully",
                    "You are checked in for " + appointment.getServiceName() + ". Please proceed to " + terms.lounge + ".",
                    "CHECKED_IN",
                    "/my-appointments");
        }

        if (appointment.getProviderId() != null) {
            notifyProvider(appointment.getProviderId(), tenantId,
                    terms.customer + " Checked In",
                    terms.customer + " " + appointment.getPatientName() + " has arrived and checked in for " + appointment.getServiceName() + ".",
                    "CHECKED_IN",
                    "/provider-dashboard");
        }

        if (tenantId != null) {
            notifyTenantAdmin(tenantId,
                    terms.customer + " Checked In",
                    appointment.getPatientName() + " (" + terms.customer + ") checked in for " + appointment.getServiceName() + ".",
                    "CHECKED_IN",
                    "/admin/appointments?search=" + appointment.getId());
        }
    }

    @Transactional
    public void notifyAppointmentRescheduled(Appointment appointment, LocalDate newDate, LocalTime newTime) {
        if (appointment == null) return;
        DynamicTerms terms = getTermsForAppointment(appointment);
        Long tenantId = appointment.getTenantId();
        Long patientUserId = resolvePatientUserId(appointment);

        if (patientUserId != null) {
            notifyUser(patientUserId,
                    terms.session + " Rescheduled",
                    "Your " + terms.session.toLowerCase() + " for " + appointment.getServiceName() + " has been rescheduled to " + newDate + " at " + newTime + ".",
                    "RESCHEDULED",
                    "/my-appointments");
        }

        if (appointment.getProviderId() != null) {
            notifyProvider(appointment.getProviderId(), tenantId,
                    terms.session + " Rescheduled",
                    terms.session + " with " + terms.customer.toLowerCase() + " " + appointment.getPatientName() + " has been rescheduled to " + newDate + " at " + newTime + ".",
                    "RESCHEDULED",
                    "/master-calendar");
        }

        if (tenantId != null) {
            notifyTenantAdmin(tenantId,
                    terms.session + " Rescheduled",
                    terms.session + " #" + appointment.getId() + " (" + appointment.getPatientName() + ") was rescheduled to " + newDate + " at " + newTime + ".",
                    "RESCHEDULED",
                    "/admin/appointments?search=" + appointment.getId());
        }
    }

    @Transactional
    public void notifyAppointmentCompleted(Appointment appointment) {
        if (appointment == null) return;
        DynamicTerms terms = getTermsForAppointment(appointment);
        Long tenantId = appointment.getTenantId();
        Long patientUserId = resolvePatientUserId(appointment);

        if (patientUserId != null) {
            notifyUser(patientUserId,
                    terms.session + " Completed",
                    "Your " + terms.session.toLowerCase() + " for " + appointment.getServiceName() + " has been marked completed. Thank you!",
                    "COMPLETED",
                    "/my-appointments");
        }

        if (appointment.getProviderId() != null) {
            notifyProvider(appointment.getProviderId(), tenantId,
                    terms.session + " Completed",
                    terms.session + " with " + terms.customer.toLowerCase() + " " + appointment.getPatientName() + " has been marked completed.",
                    "COMPLETED",
                    "/provider-dashboard");
        }

        if (tenantId != null) {
            notifyTenantAdmin(tenantId,
                    terms.session + " Completed",
                    terms.session + " #" + appointment.getId() + " (" + appointment.getPatientName() + ") was marked completed.",
                    "COMPLETED",
                    "/admin/appointments?search=" + appointment.getId());
        }
    }

    @Transactional
    public void notifyAppointmentCancelled(Appointment appointment) {
        if (appointment == null) return;
        DynamicTerms terms = getTermsForAppointment(appointment);
        Long tenantId = appointment.getTenantId();
        Long patientUserId = resolvePatientUserId(appointment);

        if (patientUserId != null) {
            notifyUser(patientUserId,
                    terms.session + " Cancelled",
                    "Your " + terms.session.toLowerCase() + " on " + appointment.getAppointmentDate() + " has been cancelled.",
                    "CANCELLED",
                    "/my-appointments");
        }

        if (appointment.getProviderId() != null) {
            notifyProvider(appointment.getProviderId(), tenantId,
                    terms.session + " Cancelled",
                    terms.session + " with " + terms.customer.toLowerCase() + " " + appointment.getPatientName() + " on " + appointment.getAppointmentDate() + " was cancelled.",
                    "CANCELLED",
                    "/master-calendar");
        }

        if (tenantId != null) {
            notifyTenantAdmin(tenantId,
                    terms.session + " Cancelled",
                    terms.session + " #" + appointment.getId() + " (" + appointment.getPatientName() + ") was cancelled.",
                    "CANCELLED",
                    "/admin/appointments?search=" + appointment.getId());
        }
    }

    @Transactional
    public void notifyVideoCallToggled(Appointment appointment, String providerName, boolean enabled) {
        if (appointment == null) return;
        DynamicTerms terms = getTermsForAppointment(appointment);
        Long tenantId = appointment.getTenantId();
        Long patientUserId = resolvePatientUserId(appointment);

        String actor = (providerName != null && !providerName.isBlank()) ? providerName : terms.provider;

        if (enabled) {
            if (patientUserId != null) {
                notifyUser(patientUserId,
                        terms.videoTitle + " Enabled",
                        actor + " enabled virtual video room for your " + terms.session.toLowerCase() + " for " + appointment.getServiceName() + ". Click here to join.",
                        "BOOKING_CONFIRMED",
                        "/my-appointments?joinVideo=" + appointment.getId());
            }

            if (tenantId != null) {
                notifyTenantAdmin(tenantId,
                        terms.videoTitle + " Ready",
                        actor + " enabled virtual video room for " + terms.session.toLowerCase() + " #" + appointment.getId() + " (" + appointment.getPatientName() + ").",
                        "NEW_APPOINTMENT",
                        "/admin/appointments?search=" + appointment.getId());
            }
        } else {
            if (patientUserId != null) {
                notifyUser(patientUserId,
                        terms.videoTitle + " Disabled",
                        "Virtual video consultation was turned off for your " + terms.session.toLowerCase() + " for " + appointment.getServiceName() + ".",
                        "NEW_APPOINTMENT",
                        "/my-appointments");
            }
        }
    }

    private Long resolvePatientUserId(Appointment appointment) {
        if (appointment.getBookedByUserId() != null) {
            return appointment.getBookedByUserId();
        }
        if (appointment.getPatientEmail() != null) {
            return userRepository.findByEmailIgnoringTenant(appointment.getPatientEmail())
                    .map(User::getId)
                    .orElse(null);
        }
        return null;
    }

    // Scoped retrieval based on user role and tenant
    public List<Notification> getNotificationsForUser(User user, String search) {
        if (user == null) return List.of();
        String role = (user.getRole() != null ? user.getRole() : "").toLowerCase();
        Long userId = user.getId();
        Long tenantId = user.getTenant() != null ? user.getTenant().getId() : null;

        boolean hasSearch = search != null && !search.trim().isEmpty();
        String q = hasSearch ? search.trim() : null;

        if (role.equals("super_admin") || role.equals("superadmin")) {
            return hasSearch
                    ? notificationRepository.searchSuperAdminNotifications(userId, q)
                    : notificationRepository.findSuperAdminNotifications(userId);
        } else if (role.equals("admin")) {
            if (tenantId == null) return List.of();
            return hasSearch
                    ? notificationRepository.searchAdminNotifications(userId, tenantId, q)
                    : notificationRepository.findAdminNotifications(userId, tenantId);
        } else if (role.equals("service_provider") || role.equals("provider")) {
            if (tenantId == null) return List.of();
            return hasSearch
                    ? notificationRepository.searchProviderNotifications(userId, tenantId, q)
                    : notificationRepository.findProviderNotifications(userId, tenantId);
        } else {
            // Standard User / Patient / Student
            return hasSearch
                    ? notificationRepository.searchUserNotifications(userId, q)
                    : notificationRepository.findUserNotifications(userId);
        }
    }

    public long getUnreadCountForUser(User user) {
        if (user == null) return 0;
        String role = (user.getRole() != null ? user.getRole() : "").toLowerCase();
        Long userId = user.getId();
        Long tenantId = user.getTenant() != null ? user.getTenant().getId() : null;

        if (role.equals("super_admin") || role.equals("superadmin")) {
            return notificationRepository.countSuperAdminUnread(userId);
        } else if (role.equals("admin")) {
            if (tenantId == null) return 0;
            return notificationRepository.countAdminUnread(userId, tenantId);
        } else if (role.equals("service_provider") || role.equals("provider")) {
            if (tenantId == null) return 0;
            return notificationRepository.countProviderUnread(userId, tenantId);
        } else {
            return notificationRepository.countUserUnread(userId);
        }
    }

    @Transactional
    public void markAllReadForUser(User user) {
        if (user == null) return;
        String role = (user.getRole() != null ? user.getRole() : "").toLowerCase();
        Long userId = user.getId();
        Long tenantId = user.getTenant() != null ? user.getTenant().getId() : null;

        if (role.equals("super_admin") || role.equals("superadmin")) {
            notificationRepository.markSuperAdminAsRead(userId);
        } else if (role.equals("admin")) {
            if (tenantId != null) notificationRepository.markAdminAsRead(userId, tenantId);
        } else if (role.equals("service_provider") || role.equals("provider")) {
            if (tenantId != null) notificationRepository.markProviderAsRead(userId, tenantId);
        } else {
            notificationRepository.markUserAsRead(userId);
        }
    }

    @Transactional
    public boolean markSingleAsRead(Long notificationId, User user) {
        Notification n = notificationRepository.findById(notificationId).orElse(null);
        if (n == null) return false;
        // Verify authorization
        String role = (user.getRole() != null ? user.getRole() : "").toLowerCase();
        Long userId = user.getId();
        Long tenantId = user.getTenant() != null ? user.getTenant().getId() : null;

        boolean authorized = false;
        if (role.equals("super_admin") || role.equals("superadmin")) {
            authorized = (n.getUserId() != null && n.getUserId().equals(userId)) || 
                         ("SUPER_ADMIN".equalsIgnoreCase(n.getTargetRole()) && n.getTenantId() == null);
        } else if (role.equals("admin")) {
            authorized = tenantId != null && tenantId.equals(n.getTenantId());
        } else if (role.equals("service_provider") || role.equals("provider")) {
            authorized = (n.getUserId() != null && n.getUserId().equals(userId)) ||
                         (tenantId != null && tenantId.equals(n.getTenantId()) && "SERVICE_PROVIDER".equalsIgnoreCase(n.getTargetRole()));
        } else {
            authorized = n.getUserId() != null && n.getUserId().equals(userId);
        }

        if (authorized) {
            n.setRead(true);
            notificationRepository.save(n);
            return true;
        }
        return false;
    }

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void markAllAsRead() {
        notificationRepository.markAllAsRead();
    }
}
