package com.backend.service;

import com.backend.model.Tenant;
import com.backend.model.User;
import com.backend.repository.TenantRepository;
import com.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionSchedulerService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    /**
     * Executes automatic subscription expiration checks and renewal reminder dispatches.
     * Runs every hour (and 10s after application boot).
     */
    @Scheduled(initialDelay = 10000, fixedRate = 3600000)
    @Transactional
    public void runExpiryCheckNow() {
        log.info("Running automatic subscription expiration & renewal reminder scan...");
        LocalDate today = LocalDate.now();
        List<Tenant> tenants = tenantRepository.findAll();

        for (Tenant tenant : tenants) {
            LocalDate expiry = tenant.getSubscriptionExpiryDate();
            if (expiry == null) {
                continue;
            }

            // 1. Expiration Detection
            if (today.isAfter(expiry)) {
                if (!"EXPIRED".equalsIgnoreCase(tenant.getSubscriptionStatus()) 
                        && !"SUSPENDED".equalsIgnoreCase(tenant.getSubscriptionStatus())) {
                    log.warn("Subscription for tenant '{}' (ID: {}) expired on {}. Enforcing Renewal Gate.",
                            tenant.getOrganizationName(), tenant.getId(), expiry);

                    tenant.setSubscriptionStatus("EXPIRED");
                    tenant.setStatus("SUSPENDED");
                    tenant.setLastSuspendedReason("Subscription expired on " + expiry);
                    tenantRepository.save(tenant);

                    // Notify Super Admin
                    try {
                        notificationService.notifySuperAdmin(
                                "Subscription Expired: " + tenant.getOrganizationName(),
                                "Subscription for " + tenant.getOrganizationName() + " has expired. Workspace access paused until renewal.",
                                "SUBSCRIPTION_EXPIRED",
                                "/superadmin/settings?tab=Billing"
                        );
                    } catch (Exception ignored) {}

                    // Notify Tenant Admin
                    dispatchTenantNotification(
                            tenant,
                            "Subscription Expired - Renewal Required",
                            "Your " + (tenant.getSubscriptionTier() != null ? tenant.getSubscriptionTier() : "Enterprise")
                                    + " subscription expired on " + expiry + ". Please renew your plan or submit an emergency extension request to restore workspace access.",
                            "SUBSCRIPTION_EXPIRED"
                    );
                }
            } else {
                // 2. Upcoming Expiry Reminders
                long daysRemaining = ChronoUnit.DAYS.between(today, expiry);

                if (daysRemaining <= 7 && "ACTIVE".equalsIgnoreCase(tenant.getSubscriptionStatus())) {
                    tenant.setSubscriptionStatus("EXPIRING_SOON");
                    tenantRepository.save(tenant);
                }

                // Reminder threshold check: 7, 3, 1 day
                if (daysRemaining == 7 || daysRemaining == 3 || daysRemaining == 1 || daysRemaining == 0) {
                    Set<String> sentSet = new HashSet<>();
                    if (tenant.getLastReminderDaysSent() != null && !tenant.getLastReminderDaysSent().isBlank()) {
                        sentSet.addAll(Arrays.asList(tenant.getLastReminderDaysSent().split(",")));
                    }

                    String key = String.valueOf(daysRemaining);
                    if (!sentSet.contains(key)) {
                        sentSet.add(key);
                        tenant.setLastReminderDaysSent(String.join(",", sentSet));
                        tenantRepository.save(tenant);

                        String dayMsg = daysRemaining == 0 ? "today" : ("in " + daysRemaining + " day" + (daysRemaining > 1 ? "s" : ""));
                        log.info("Dispatching {} renewal reminder to tenant '{}'", dayMsg, tenant.getOrganizationName());

                        dispatchTenantNotification(
                                tenant,
                                "Subscription Expiring " + (daysRemaining == 0 ? "Today" : ("in " + daysRemaining + " Days")),
                                "Your OmniBook subscription expires " + dayMsg + " (" + expiry + "). Renew now to avoid any interruption to client booking or queue management.",
                                "SUBSCRIPTION_EXPIRING_SOON"
                        );
                    }
                }
            }
        }
    }

    private void dispatchTenantNotification(Tenant tenant, String title, String message, String type) {
        try {
            List<User> admins = userRepository.findByTenantIdAndRole(tenant.getId(), "admin");
            for (User admin : admins) {
                notificationService.createNotification(
                        admin.getId(),
                        "admin",
                        tenant.getId(),
                        title,
                        message,
                        type,
                        "/admin/subscription"
                );
            }
        } catch (Exception e) {
            log.error("Failed to dispatch tenant expiration notification: {}", e.getMessage());
        }
    }
}
