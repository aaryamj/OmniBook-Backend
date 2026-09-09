package com.backend.service;

import com.backend.dto.*;
import com.backend.model.Notification;
import com.backend.model.SupportTicket;
import com.backend.model.SystemAnnouncement;
import com.backend.repository.NotificationRepository;
import com.backend.repository.SupportTicketRepository;
import com.backend.repository.SystemAnnouncementRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportTicketService {

    private final SupportTicketRepository supportTicketRepository;
    private final SystemAnnouncementRepository systemAnnouncementRepository;
    private final NotificationRepository notificationRepository;
    private final com.backend.repository.NewsletterSubscriberRepository newsletterSubscriberRepository;
    private final com.backend.repository.UserRepository userRepository;
    private final EmailService emailService;

    @PostConstruct
    public void initSeedData() {
        if (supportTicketRepository.count() == 0) {
            log.info("Seeding initial support escalation tickets...");

            SupportTicket t1 = SupportTicket.builder()
                    .ticketNumber("#TK-8821")
                    .organizationName("Lalitpur Wellness Center")
                    .organizationType("Salon")
                    .requesterName("Admin Lalitpur")
                    .adminAccount("admin@lalitpurwell.np")
                    .issueType("Integration Bugs")
                    .subject("Google Calendar Webhook Sync Latency")
                    .message("Client bookings on front-desk take up to 45 seconds to reflect on specialist calendars.")
                    .priority("Normal")
                    .status("In Progress")
                    .slaMinutesRemaining(42)
                    .resolutionHours(1.2)
                    .csatRating(98.5)
                    .createdAt(LocalDateTime.now().minusHours(2))
                    .build();

            SupportTicket t2 = SupportTicket.builder()
                    .ticketNumber("#TK-8819")
                    .organizationName("Kantipath Clinic")
                    .organizationType("Clinic")
                    .requesterName("Ops Kantipath")
                    .adminAccount("ops@kantipath.org")
                    .issueType("Critical (SLA)")
                    .subject("Cardiology Room Conflict Detection Failure")
                    .message("Two patients scheduled simultaneously in Room 302 without buffer notification trigger.")
                    .priority("Urgent")
                    .status("Urgent")
                    .slaMinutesRemaining(12)
                    .resolutionHours(0.8)
                    .csatRating(97.0)
                    .createdAt(LocalDateTime.now().minusHours(1))
                    .build();

            SupportTicket t3 = SupportTicket.builder()
                    .ticketNumber("#TK-8794")
                    .organizationName("Bir Hospital Engine")
                    .organizationType("Clinic")
                    .requesterName("Tech Support Bir")
                    .adminAccount("tech@bir.org")
                    .issueType("Integration Bugs")
                    .subject("SMS Gateway Timeout During Peak Hours")
                    .message("Recall reminders dispatched at 09:00 AM experiencing 10% carrier bounce rates.")
                    .priority("Normal")
                    .status("Open")
                    .slaMinutesRemaining(240)
                    .resolutionHours(1.5)
                    .csatRating(99.0)
                    .createdAt(LocalDateTime.now().minusHours(4))
                    .build();

            SupportTicket t4 = SupportTicket.builder()
                    .ticketNumber("#TK-8750")
                    .organizationName("Mediciti Core")
                    .organizationType("Enterprise")
                    .requesterName("Billing Lead")
                    .adminAccount("billing@mediciti.com")
                    .issueType("Billing Issues")
                    .subject("Subscription Ledger Reconciliation Variance")
                    .message("Reconciliation report indicates mismatched invoice tax summary for August.")
                    .priority("Normal")
                    .status("Closed")
                    .slaMinutesRemaining(0)
                    .resolutionHours(2.1)
                    .csatRating(99.2)
                    .createdAt(LocalDateTime.now().minusDays(2))
                    .resolvedAt(LocalDateTime.now().minusDays(1))
                    .build();

            supportTicketRepository.saveAll(List.of(t1, t2, t3, t4));
            log.info("Successfully seeded 4 escalation queue tickets.");
        }
    }

    @Transactional
    public SupportTicketResponseDTO createPublicTicket(SupportTicketRequestDTO request) {
        String ticketNum = "#TK-" + (1000 + new Random().nextInt(9000));
        
        // Map category to standard issue type
        String mappedIssueType = "Integration Bugs";
        if (request.getCategory() != null) {
            String cat = request.getCategory().toLowerCase();
            if (cat.contains("billing")) {
                mappedIssueType = "Billing Issues";
            } else if (cat.contains("onboarding") || cat.contains("urgent") || cat.contains("critical")) {
                mappedIssueType = "Critical (SLA)";
            } else if (cat.contains("technical") || cat.contains("feature") || cat.contains("integration")) {
                mappedIssueType = "Integration Bugs";
            } else {
                mappedIssueType = request.getCategory();
            }
        }

        String orgName = request.getOrganizationName();
        if (orgName == null || orgName.isBlank()) {
            // Derive organization name nicely from email domain
            try {
                String domain = request.getEmail().substring(request.getEmail().indexOf("@") + 1);
                String brand = domain.split("\\.")[0];
                orgName = brand.substring(0, 1).toUpperCase() + brand.substring(1) + " Workspace";
            } catch (Exception e) {
                orgName = "Public Workspace";
            }
        }

        SupportTicket ticket = SupportTicket.builder()
                .ticketNumber(ticketNum)
                .organizationName(orgName)
                .organizationType(request.getOrganizationType() != null ? request.getOrganizationType() : "General")
                .requesterName(request.getRequesterName() != null ? request.getRequesterName() : "Support Contact")
                .adminAccount(request.getEmail().trim())
                .issueType(mappedIssueType)
                .subject("Support Request: " + (request.getCategory() != null ? request.getCategory() : "General Inquiry"))
                .message(request.getMessage())
                .priority("Normal")
                .status("Open")
                .slaMinutesRemaining(180)
                .resolutionHours(1.4)
                .csatRating(98.5)
                .createdAt(LocalDateTime.now())
                .build();

        SupportTicket saved = supportTicketRepository.save(ticket);
        log.info("Created new public support ticket {} for {}", saved.getTicketNumber(), saved.getAdminAccount());
        return mapToDTO(saved);
    }

    public List<SupportTicketResponseDTO> getTickets(String timeFilter, String issueType) {
        LocalDateTime startDate = parseTimeFilter(timeFilter);

        List<SupportTicket> tickets;
        boolean hasTypeFilter = issueType != null && !issueType.equalsIgnoreCase("All Tickets") && !issueType.isBlank();

        if (startDate != null && hasTypeFilter) {
            tickets = supportTicketRepository.findByCreatedAtAfterAndIssueType(startDate, issueType);
        } else if (startDate != null) {
            tickets = supportTicketRepository.findByCreatedAtAfter(startDate);
        } else if (hasTypeFilter) {
            tickets = supportTicketRepository.findByIssueType(issueType);
        } else {
            tickets = supportTicketRepository.findAllByOrderByCreatedAtDesc();
        }

        return tickets.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public SupportKPIDTO getSupportKPIs(String timeFilter) {
        LocalDateTime startDate = parseTimeFilter(timeFilter);

        long openCount;
        long slaBreaches;
        Double avgRes;
        Double csat;

        if (startDate != null) {
            openCount = supportTicketRepository.countOpenTicketsSince(startDate);
            slaBreaches = supportTicketRepository.countSlaBreachesSince(startDate);
            avgRes = supportTicketRepository.getAverageResolutionHoursSince(startDate);
            csat = supportTicketRepository.getAverageCsatScoreSince(startDate);
        } else {
            openCount = supportTicketRepository.countOpenTickets();
            slaBreaches = supportTicketRepository.countSlaBreaches();
            avgRes = supportTicketRepository.getAverageResolutionHours();
            csat = supportTicketRepository.getAverageCsatScore();
        }

        double finalAvgRes = (avgRes != null && avgRes > 0) ? avgRes : 1.4;
        double finalCsat = (csat != null && csat > 0) ? csat : 98.4;

        return SupportKPIDTO.builder()
                .openTickets(openCount)
                .avgResolution(String.format("%.1fh", finalAvgRes))
                .slaBreaches(slaBreaches)
                .csatScore(String.format("%.1f%%", finalCsat))
                .build();
    }

    @Transactional
    public SupportTicketResponseDTO updateTicketStatus(Long id, String newStatus) {
        SupportTicket ticket = supportTicketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found with id: " + id));

        ticket.setStatus(newStatus);
        if ("Resolved".equalsIgnoreCase(newStatus) || "Closed".equalsIgnoreCase(newStatus)) {
            ticket.setResolvedAt(LocalDateTime.now());
            ticket.setSlaMinutesRemaining(0);
        }
        SupportTicket saved = supportTicketRepository.save(ticket);

        // Dispatch status update email to the ticket requester
        String recipient = saved.getAdminAccount();
        if (recipient != null && recipient.contains("@")) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    emailService.sendTicketStatusUpdateEmail(
                            recipient.trim(),
                            saved.getTicketNumber(),
                            saved.getOrganizationName(),
                            saved.getStatus(),
                            saved.getIssueType(),
                            saved.getSubject(),
                            saved.getMessage()
                    );
                } catch (Exception e) {
                    log.error("Failed to send status update email for ticket {}: {}", saved.getTicketNumber(), e.getMessage());
                }
            });
        }

        return mapToDTO(saved);
    }

    @Transactional
    public com.backend.model.NewsletterSubscriber subscribeNewsletter(NewsletterSubscribeRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        return newsletterSubscriberRepository.findByEmail(email).orElseGet(() -> {
            com.backend.model.NewsletterSubscriber sub = com.backend.model.NewsletterSubscriber.builder()
                    .email(email)
                    .status("ACTIVE")
                    .source(request.getSource() != null ? request.getSource() : "LANDING_FOOTER")
                    .subscribedAt(LocalDateTime.now())
                    .build();
            return newsletterSubscriberRepository.save(sub);
        });
    }

    @Transactional
    public SystemAnnouncement createAnnouncement(AnnouncementRequestDTO request, String createdBy) {
        SystemAnnouncement announcement = SystemAnnouncement.builder()
                .title(request.getTitle())
                .messageBody(request.getMessageBody())
                .audience(request.getAudience() != null ? request.getAudience() : "All Users")
                .targetRoles(request.getTargetRoles())
                .priority(request.getPriority() != null ? request.getPriority() : "Normal")
                .inAppBanner(request.getInAppBanner() != null ? request.getInAppBanner() : true)
                .emailAlert(request.getEmailAlert() != null ? request.getEmailAlert() : true)
                .createdBy(createdBy != null ? createdBy : "Super Admin")
                .createdAt(LocalDateTime.now())
                .build();

        SystemAnnouncement saved = systemAnnouncementRepository.save(announcement);

        // If in-app banner is true, broadcast system notification
        if (Boolean.TRUE.equals(request.getInAppBanner())) {
            try {
                Notification notice = Notification.builder()
                        .title("SYSTEM ANNOUNCEMENT: " + request.getTitle())
                        .message(request.getMessageBody())
                        .type("SYSTEM_ANNOUNCEMENT")
                        .isRead(false)
                        .createdAt(LocalDateTime.now())
                        .build();
                notificationRepository.save(notice);
            } catch (Exception e) {
                log.warn("Could not create system notification entry: {}", e.getMessage());
            }
        }

        // Broadcast email to ALL emails in database (all users, admins, service_providers, and newsletter subscribers)
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                java.util.Set<String> recipientEmails = new java.util.HashSet<>();
                // 1. All users in database (user, admin, service_provider, super_admin)
                java.util.List<String> userEmails = userRepository.findAllUserEmailsIgnoringTenant();
                if (userEmails != null) {
                    recipientEmails.addAll(userEmails);
                }
                // 2. All newsletter subscribers in database
                java.util.List<String> subEmails = newsletterSubscriberRepository.findAllActiveSubscriberEmails();
                if (subEmails != null) {
                    recipientEmails.addAll(subEmails);
                }

                log.info("Broadcasting system announcement '{}' to {} total recipient emails", 
                        request.getTitle(), recipientEmails.size());

                for (String email : recipientEmails) {
                    if (email != null && email.contains("@")) {
                        try {
                            emailService.sendSystemAnnouncementEmail(
                                    email.trim(), 
                                    request.getTitle(), 
                                    request.getMessageBody(), 
                                    request.getPriority()
                            );
                        } catch (Exception ex) {
                            log.error("Failed to send announcement email to {}: {}", email, ex.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error during background announcement email broadcast: {}", e.getMessage(), e);
            }
        });

        return saved;
    }

    public List<SystemAnnouncement> getAllAnnouncements() {
        return systemAnnouncementRepository.findAllByOrderByCreatedAtDesc();
    }

    private LocalDateTime parseTimeFilter(String filter) {
        if (filter == null) return null;
        return switch (filter.trim().toLowerCase()) {
            case "last 7 days" -> LocalDateTime.now().minusDays(7);
            case "last 30 days" -> LocalDateTime.now().minusDays(30);
            case "last 90 days" -> LocalDateTime.now().minusDays(90);
            default -> null;
        };
    }

    private SupportTicketResponseDTO mapToDTO(SupportTicket t) {
        // Compute visual badges
        String slaTimeRemaining;
        String slaStatusClass;
        String slaTextColor;
        String statusBg;
        String statusText;

        String status = t.getStatus() != null ? t.getStatus() : "Open";

        if ("Closed".equalsIgnoreCase(status) || "Resolved".equalsIgnoreCase(status)) {
            slaTimeRemaining = "Resolved";
            slaStatusClass = "check_circle";
            slaTextColor = "text-green-700";
            statusBg = "bg-green-100";
            statusText = "text-green-700";
        } else if ("Urgent".equalsIgnoreCase(status) || "Critical".equalsIgnoreCase(t.getPriority())) {
            int mins = t.getSlaMinutesRemaining() != null ? t.getSlaMinutesRemaining() : 12;
            slaTimeRemaining = mins + " mins remaining";
            slaStatusClass = "bg-yellow-500";
            slaTextColor = "text-yellow-700 font-bold";
            statusBg = "bg-red-100";
            statusText = "text-red-700";
        } else if ("In Progress".equalsIgnoreCase(status)) {
            int mins = t.getSlaMinutesRemaining() != null ? t.getSlaMinutesRemaining() : 45;
            slaTimeRemaining = mins + " mins remaining";
            slaStatusClass = "bg-green-500";
            slaTextColor = "text-on-surface";
            statusBg = "bg-blue-100";
            statusText = "text-blue-700";
        } else {
            // Open
            int mins = t.getSlaMinutesRemaining() != null ? t.getSlaMinutesRemaining() : 120;
            if (mins >= 60) {
                slaTimeRemaining = (mins / 60) + " hours remaining";
            } else {
                slaTimeRemaining = mins + " mins remaining";
            }
            slaStatusClass = "bg-green-500";
            slaTextColor = "text-on-surface";
            statusBg = "bg-gray-100";
            statusText = "text-gray-700";
        }

        return SupportTicketResponseDTO.builder()
                .id(t.getId())
                .ticketNumber(t.getTicketNumber())
                .organizationName(t.getOrganizationName())
                .organizationType(t.getOrganizationType())
                .requesterName(t.getRequesterName())
                .adminAccount(t.getAdminAccount())
                .issueType(t.getIssueType())
                .subject(t.getSubject())
                .message(t.getMessage())
                .priority(t.getPriority())
                .status(status)
                .slaTimeRemaining(slaTimeRemaining)
                .slaStatusClass(slaStatusClass)
                .slaTextColor(slaTextColor)
                .statusBg(statusBg)
                .statusText(statusText)
                .createdAt(t.getCreatedAt())
                .build();
    }
}
