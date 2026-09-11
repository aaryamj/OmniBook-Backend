package com.backend.service;

import com.backend.dto.CancelRequestDTO;
import com.backend.dto.RescheduleRequestDTO;
import com.backend.dto.TenantPolicyDTO;
import com.backend.dto.TimeSlotDTO;
import com.backend.model.Appointment;
import com.backend.model.AppointmentLifecycleEvent;
import com.backend.model.Tenant;
import com.backend.model.User;
import com.backend.repository.AppointmentLifecycleEventRepository;
import com.backend.repository.AppointmentRepository;
import com.backend.repository.TenantRepository;
import com.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentLifecycleService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentLifecycleEventRepository lifecycleEventRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final PublicBookingService publicBookingService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final CommissionService commissionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get tenant cancellation and rescheduling policy.
     */
    public TenantPolicyDTO getTenantPolicy(Long tenantId) {
        Tenant tenant = tenantId != null ? tenantRepository.findById(tenantId).orElse(null) : null;
        if (tenant == null) {
            return TenantPolicyDTO.builder()
                    .cancellationAllowed(true)
                    .cancellationDeadlineHours(6)
                    .fullRefundHours(24)
                    .partialRefundPercentage(50.0)
                    .lateRefundPercentage(0.0)
                    .reschedulingAllowed(true)
                    .maxReschedules(2)
                    .reschedulingDeadlineHours(6)
                    .autoProcessRefunds(true)
                    .noShowGracePeriodMinutes(15)
                    .noShowRefundPercentage(0.0)
                    .autoClassifyNoShow(true)
                    .defaultCommissionRate(10.0)
                    .build();
        }

        return TenantPolicyDTO.builder()
                .tenantId(tenant.getId())
                .organizationName(tenant.getOrganizationName())
                .organizationType(tenant.getOrganizationType())
                .cancellationAllowed(tenant.getCancellationAllowed() != null ? tenant.getCancellationAllowed() : true)
                .cancellationDeadlineHours(tenant.getCancellationDeadlineHours() != null ? tenant.getCancellationDeadlineHours() : 6)
                .fullRefundHours(tenant.getFullRefundHours() != null ? tenant.getFullRefundHours() : 24)
                .partialRefundPercentage(tenant.getPartialRefundPercentage() != null ? tenant.getPartialRefundPercentage() : 50.0)
                .lateRefundPercentage(tenant.getLateRefundPercentage() != null ? tenant.getLateRefundPercentage() : 0.0)
                .reschedulingAllowed(tenant.getReschedulingAllowed() != null ? tenant.getReschedulingAllowed() : true)
                .maxReschedules(tenant.getMaxReschedules() != null ? tenant.getMaxReschedules() : 2)
                .reschedulingDeadlineHours(tenant.getReschedulingDeadlineHours() != null ? tenant.getReschedulingDeadlineHours() : 6)
                .autoProcessRefunds(tenant.getAutoProcessRefunds() != null ? tenant.getAutoProcessRefunds() : true)
                .noShowGracePeriodMinutes(tenant.getNoShowGracePeriodMinutes() != null ? tenant.getNoShowGracePeriodMinutes() : 15)
                .noShowRefundPercentage(tenant.getNoShowRefundPercentage() != null ? tenant.getNoShowRefundPercentage() : 0.0)
                .autoClassifyNoShow(tenant.getAutoClassifyNoShow() != null ? tenant.getAutoClassifyNoShow() : true)
                .defaultCommissionRate(tenant.getDefaultCommissionRate() != null ? tenant.getDefaultCommissionRate() : 10.0)
                .build();
    }

    /**
     * Update tenant policy by admin.
     */
    @Transactional
    public TenantPolicyDTO updateTenantPolicy(Long tenantId, TenantPolicyDTO dto) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found with id " + tenantId));

        if (dto.getCancellationAllowed() != null) tenant.setCancellationAllowed(dto.getCancellationAllowed());
        if (dto.getCancellationDeadlineHours() != null) tenant.setCancellationDeadlineHours(Math.max(0, dto.getCancellationDeadlineHours()));
        if (dto.getFullRefundHours() != null) tenant.setFullRefundHours(Math.max(0, dto.getFullRefundHours()));
        if (dto.getPartialRefundPercentage() != null) tenant.setPartialRefundPercentage(Math.max(0.0, Math.min(100.0, dto.getPartialRefundPercentage())));
        if (dto.getLateRefundPercentage() != null) tenant.setLateRefundPercentage(Math.max(0.0, Math.min(100.0, dto.getLateRefundPercentage())));
        if (dto.getReschedulingAllowed() != null) tenant.setReschedulingAllowed(dto.getReschedulingAllowed());
        if (dto.getMaxReschedules() != null) tenant.setMaxReschedules(Math.max(0, dto.getMaxReschedules()));
        if (dto.getReschedulingDeadlineHours() != null) tenant.setReschedulingDeadlineHours(Math.max(0, dto.getReschedulingDeadlineHours()));
        if (dto.getAutoProcessRefunds() != null) tenant.setAutoProcessRefunds(dto.getAutoProcessRefunds());
        if (dto.getNoShowGracePeriodMinutes() != null) tenant.setNoShowGracePeriodMinutes(Math.max(1, dto.getNoShowGracePeriodMinutes()));
        if (dto.getNoShowRefundPercentage() != null) tenant.setNoShowRefundPercentage(Math.max(0.0, Math.min(100.0, dto.getNoShowRefundPercentage())));
        if (dto.getAutoClassifyNoShow() != null) tenant.setAutoClassifyNoShow(dto.getAutoClassifyNoShow());
        if (dto.getDefaultCommissionRate() != null) tenant.setDefaultCommissionRate(Math.max(0.0, Math.min(100.0, dto.getDefaultCommissionRate())));

        tenantRepository.save(tenant);
        return getTenantPolicy(tenantId);
    }

    /**
     * Previews cancellation eligibility, refund tier, and estimated refund amount for an appointment.
     */
    public Map<String, Object> previewCancellation(Long appointmentId, String userEmail) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        validateAccess(appointment, userEmail);

        Map<String, Object> result = new HashMap<>();
        result.put("appointmentId", appointment.getId());
        result.put("serviceName", appointment.getServiceName());
        result.put("appointmentDate", appointment.getAppointmentDate());
        result.put("appointmentTime", appointment.getAppointmentTime());
        result.put("currentStatus", appointment.getAppointmentStatus());
        result.put("paymentMethod", appointment.getPaymentMethod());
        result.put("paymentStatus", appointment.getPaymentStatus());

        if ("CANCELLED".equalsIgnoreCase(appointment.getAppointmentStatus())) {
            result.put("canCancel", false);
            result.put("message", "Appointment is already cancelled.");
            return result;
        }

        if ("COMPLETED".equalsIgnoreCase(appointment.getAppointmentStatus())) {
            result.put("canCancel", false);
            result.put("message", "Cannot cancel a completed appointment.");
            return result;
        }

        TenantPolicyDTO policy = getTenantPolicy(appointment.getTenantId());
        result.put("policy", policy);

        if (!Boolean.TRUE.equals(policy.getCancellationAllowed())) {
            result.put("canCancel", false);
            result.put("message", "Cancellation is currently disabled by organization policy.");
            return result;
        }

        LocalDateTime apptDateTime = LocalDateTime.of(appointment.getAppointmentDate(), appointment.getAppointmentTime());
        LocalDateTime now = LocalDateTime.now();

        double hoursUntilAppointment = (double) Duration.between(now, apptDateTime).toMinutes() / 60.0;
        result.put("hoursUntilAppointment", Math.round(hoursUntilAppointment * 10.0) / 10.0);

        // Refund calculation
        double refundPercentage = 0.0;
        String tier = "NO_REFUND";

        if (now.isAfter(apptDateTime)) {
            // Past appointment / no-show
            refundPercentage = 0.0;
            tier = "NO_SHOW_OR_PAST";
        } else if (hoursUntilAppointment >= policy.getFullRefundHours()) {
            // 24+ hours
            refundPercentage = 100.0;
            tier = "FULL_REFUND";
        } else if (hoursUntilAppointment >= policy.getCancellationDeadlineHours()) {
            // 6-24 hours
            refundPercentage = policy.getPartialRefundPercentage() != null ? policy.getPartialRefundPercentage() : 50.0;
            tier = "PARTIAL_REFUND";
        } else {
            // Less than 6 hours
            refundPercentage = policy.getLateRefundPercentage() != null ? policy.getLateRefundPercentage() : 0.0;
            tier = "LATE_CANCELLATION";
        }

        result.put("canCancel", true);
        result.put("refundEligibilityPercentage", refundPercentage);
        result.put("tier", tier);

        // Determine base amount and currency
        String refundCurrency = "NPR";
        double baseAmount = 0.0;

        if ("STRIPE".equalsIgnoreCase(appointment.getPaymentMethod())) {
            refundCurrency = "USD";
            baseAmount = appointment.getChargedAmount() != null ? appointment.getChargedAmount() : 0.0;
        } else if ("ESEWA".equalsIgnoreCase(appointment.getPaymentMethod())) {
            refundCurrency = "NPR";
            baseAmount = appointment.getBasePriceNpr() != null ? appointment.getBasePriceNpr() : (appointment.getPrice() != null ? appointment.getPrice() : 0.0);
        } else {
            baseAmount = appointment.getPrice() != null ? appointment.getPrice() : 0.0;
        }

        double estimatedRefundAmount = 0.0;
        if ("SUCCESS".equalsIgnoreCase(appointment.getPaymentStatus()) || "PAID".equalsIgnoreCase(appointment.getPaymentStatus())) {
            estimatedRefundAmount = Math.round((baseAmount * (refundPercentage / 100.0)) * 100.0) / 100.0;
        }

        result.put("originalPaidAmount", baseAmount);
        result.put("refundCurrency", refundCurrency);
        result.put("estimatedRefundAmount", estimatedRefundAmount);
        result.put("exchangeRate", appointment.getExchangeRate());
        result.put("originalNprAmount", appointment.getBasePriceNpr() != null ? appointment.getBasePriceNpr() : appointment.getPrice());

        return result;
    }

    /**
     * Cancels an appointment according to the organization's policy and triggers refund if eligible.
     */
    @Transactional
    public Appointment cancelAppointment(Long appointmentId, String userEmail, CancelRequestDTO request, String actorRole) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found with id " + appointmentId));

        User actorUser = userRepository.findByEmail(userEmail).orElse(null);
        validateAccess(appointment, userEmail);

        if ("CANCELLED".equalsIgnoreCase(appointment.getAppointmentStatus())) {
            throw new IllegalStateException("Appointment is already cancelled.");
        }
        if ("COMPLETED".equalsIgnoreCase(appointment.getAppointmentStatus())) {
            throw new IllegalStateException("Cannot cancel an appointment that is already completed.");
        }

        Long tenantId = appointment.getTenantId();
        TenantPolicyDTO policy = getTenantPolicy(tenantId);

        if (!Boolean.TRUE.equals(policy.getCancellationAllowed())) {
            throw new IllegalStateException("Cancellations are disabled by this organization.");
        }

        LocalDateTime apptDateTime = LocalDateTime.of(appointment.getAppointmentDate(), appointment.getAppointmentTime());
        LocalDateTime now = LocalDateTime.now();
        double hoursUntilAppointment = (double) Duration.between(now, apptDateTime).toMinutes() / 60.0;

        // Calculate refund eligibility
        double refundPercentage = 0.0;
        if (!now.isAfter(apptDateTime)) {
            if (hoursUntilAppointment >= policy.getFullRefundHours()) {
                refundPercentage = 100.0;
            } else if (hoursUntilAppointment >= policy.getCancellationDeadlineHours()) {
                refundPercentage = policy.getPartialRefundPercentage() != null ? policy.getPartialRefundPercentage() : 50.0;
            } else {
                refundPercentage = policy.getLateRefundPercentage() != null ? policy.getLateRefundPercentage() : 0.0;
            }
        }

        String refundCurrency = "NPR";
        double baseAmount = 0.0;
        if ("STRIPE".equalsIgnoreCase(appointment.getPaymentMethod())) {
            refundCurrency = "USD";
            baseAmount = appointment.getChargedAmount() != null ? appointment.getChargedAmount() : 0.0;
        } else if ("ESEWA".equalsIgnoreCase(appointment.getPaymentMethod())) {
            refundCurrency = "NPR";
            baseAmount = appointment.getBasePriceNpr() != null ? appointment.getBasePriceNpr() : (appointment.getPrice() != null ? appointment.getPrice() : 0.0);
        } else {
            baseAmount = appointment.getPrice() != null ? appointment.getPrice() : 0.0;
        }

        double refundAmount = 0.0;
        boolean hasPaid = "SUCCESS".equalsIgnoreCase(appointment.getPaymentStatus()) || "PAID".equalsIgnoreCase(appointment.getPaymentStatus());
        if (hasPaid && refundPercentage > 0.0) {
            refundAmount = Math.round((baseAmount * (refundPercentage / 100.0)) * 100.0) / 100.0;
        }

        String oldStatus = appointment.getAppointmentStatus();
        appointment.setAppointmentStatus("CANCELLED");
        appointment.setCancelledAt(now);
        appointment.setCancelledByName(actorUser != null ? actorUser.getFullName() : userEmail);
        appointment.setCancelledByRole(actorRole != null ? actorRole : (actorUser != null ? actorUser.getRole() : "patient"));
        appointment.setCancelledByUserId(actorUser != null ? actorUser.getId() : null);
        appointment.setCancellationReason(request.getReason());

        appointment.setRefundEligibilityPercentage(refundPercentage);
        appointment.setRefundAmount(refundAmount);
        appointment.setRefundCurrency(refundCurrency);

        if (refundAmount <= 0.0 || !hasPaid) {
            appointment.setRefundStatus("NOT_ELIGIBLE");
        } else {
            if (Boolean.TRUE.equals(policy.getAutoProcessRefunds())) {
                appointment.setRefundStatus("PROCESSING");
                appointment.setRefundRequestedAt(now);
                try {
                    String refundTxId = null;
                    if ("STRIPE".equalsIgnoreCase(appointment.getPaymentMethod())) {
                        String ref = appointment.getGatewayPaymentRef() != null ? appointment.getGatewayPaymentRef() : appointment.getTransactionId();
                        refundTxId = paymentService.executeStripeRefund(ref, refundAmount);
                    } else if ("ESEWA".equalsIgnoreCase(appointment.getPaymentMethod())) {
                        refundTxId = paymentService.executeEsewaRefund(appointment.getTransactionId(), refundAmount);
                    }
                    appointment.setRefundTransactionId(refundTxId);
                    appointment.setRefundStatus(refundPercentage >= 100.0 ? "REFUNDED" : "PARTIALLY_REFUNDED");
                    appointment.setRefundedAt(LocalDateTime.now());
                } catch (Exception refundEx) {
                    log.error("Automatic refund processing failed for appointment {}: {}", appointment.getId(), refundEx.getMessage());
                    appointment.setRefundStatus("FAILED");
                    appointment.setRefundFailureReason(refundEx.getMessage());
                }
            } else {
                appointment.setRefundStatus("REFUND_REQUESTED");
                appointment.setRefundRequestedAt(now);
            }
        }

        // Save appointment (Releases slot capacity automatically)
        Appointment saved = appointmentRepository.save(appointment);

        // Synchronize commission settlement records
        try {
            commissionService.adjustSettlementForNoShowOrRefund(saved.getId(), refundAmount, "CANCELLED");
        } catch (Exception commEx) {
            log.warn("Failed adjusting commission ledger for cancelled appointment {}: {}", saved.getId(), commEx.getMessage());
        }

        // Record lifecycle audit event
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("hoursUntilAppointment", Math.round(hoursUntilAppointment * 10.0) / 10.0);
        metadata.put("refundEligibilityPercentage", refundPercentage);
        metadata.put("refundAmount", refundAmount);
        metadata.put("refundCurrency", refundCurrency);
        metadata.put("refundStatus", appointment.getRefundStatus());
        metadata.put("refundTransactionId", appointment.getRefundTransactionId());
        metadata.put("settlementStatus", appointment.getSettlementStatus());
        metadata.put("settlementAmount", appointment.getSettlementAmount());
        metadata.put("netRetainedAmount", appointment.getNetRetainedAmount());
        metadata.put("gatewayFeeAmount", appointment.getGatewayFeeAmount());
        metadata.put("reason", request.getReason());

        recordLifecycleEvent(
                saved.getId(),
                tenantId,
                "CANCELLED",
                actorUser != null ? actorUser.getId() : null,
                actorUser != null ? actorUser.getFullName() : userEmail,
                actorRole != null ? actorRole : "patient",
                oldStatus,
                "CANCELLED",
                request.getReason(),
                metadata
        );

        // Dispatch notifications and emails
        try {
            notificationService.notifyAppointmentCancelled(saved);
            emailService.sendAppointmentCancelledEmail(saved);
        } catch (Exception e) {
            log.warn("Notification error after cancellation: {}", e.getMessage());
        }

        return saved;
    }

    /**
     * Reschedules an appointment to a new slot with atomic capacity verification.
     */
    @Transactional
    public Appointment rescheduleAppointment(Long appointmentId, String userEmail, RescheduleRequestDTO request, String actorRole) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found with id " + appointmentId));

        User actorUser = userRepository.findByEmail(userEmail).orElse(null);
        validateAccess(appointment, userEmail);

        if ("CANCELLED".equalsIgnoreCase(appointment.getAppointmentStatus())) {
            throw new IllegalStateException("Cannot reschedule a cancelled appointment.");
        }
        if ("COMPLETED".equalsIgnoreCase(appointment.getAppointmentStatus())) {
            throw new IllegalStateException("Cannot reschedule a completed appointment.");
        }

        Long tenantId = appointment.getTenantId();
        TenantPolicyDTO policy = getTenantPolicy(tenantId);

        if (!Boolean.TRUE.equals(policy.getReschedulingAllowed())) {
            throw new IllegalStateException("Rescheduling is disabled by this organization's policy.");
        }

        int currentReschedules = appointment.getRescheduleCount() != null ? appointment.getRescheduleCount() : 0;
        int maxAllowed = policy.getMaxReschedules() != null ? policy.getMaxReschedules() : 2;
        if (currentReschedules >= maxAllowed) {
            throw new IllegalStateException("Maximum reschedule limit of " + maxAllowed + " has been reached for this appointment.");
        }

        LocalDateTime currentApptDateTime = LocalDateTime.of(appointment.getAppointmentDate(), appointment.getAppointmentTime());
        LocalDateTime now = LocalDateTime.now();
        double hoursUntilAppointment = (double) Duration.between(now, currentApptDateTime).toMinutes() / 60.0;
        int deadlineHours = policy.getReschedulingDeadlineHours() != null ? policy.getReschedulingDeadlineHours() : 6;

        if (hoursUntilAppointment < deadlineHours) {
            throw new IllegalStateException("Appointments must be rescheduled at least " + deadlineHours + " hours in advance.");
        }

        LocalDateTime newDateTime = LocalDateTime.of(request.getNewDate(), request.getNewTime());
        if (!newDateTime.isAfter(now)) {
            throw new IllegalArgumentException("The rescheduled appointment time must be in the future.");
        }

        // Verify that target provider slot is available
        Long providerId = appointment.getProviderId();
        if (providerId != null) {
            Map<String, Object> slotData = publicBookingService.getProviderSlots(
                    providerId,
                    request.getNewDate().toString(),
                    appointment.getServiceName(),
                    userEmail,
                    actorUser != null ? actorUser.getId() : null
            );

            @SuppressWarnings("unchecked")
            List<TimeSlotDTO> availableSlots = (List<TimeSlotDTO>) slotData.get("slots");
            String targetTime24 = request.getNewTime().format(DateTimeFormatter.ofPattern("HH:mm"));

            boolean slotFoundAndAvailable = false;
            if (availableSlots != null) {
                for (TimeSlotDTO slot : availableSlots) {
                    if (targetTime24.equals(slot.getSlotTime24())) {
                        if (!Boolean.TRUE.equals(slot.getIsPast()) && !Boolean.TRUE.equals(slot.getIsBreak()) && !Boolean.TRUE.equals(slot.getIsFull())) {
                            slotFoundAndAvailable = true;
                        }
                        break;
                    }
                }
            }

            if (!slotFoundAndAvailable) {
                throw new IllegalStateException("The selected time slot " + targetTime24 + " on " + request.getNewDate() + " is full or unavailable. Please pick an open slot.");
            }
        }

        LocalDate oldDate = appointment.getAppointmentDate();
        LocalTime oldTime = appointment.getAppointmentTime();

        // Preserve original date/time if this is the first reschedule
        if (appointment.getOriginalAppointmentDate() == null) {
            appointment.setOriginalAppointmentDate(oldDate);
            appointment.setOriginalAppointmentTime(oldTime);
        }

        appointment.setAppointmentDate(request.getNewDate());
        appointment.setAppointmentTime(request.getNewTime());
        appointment.setRescheduleCount(currentReschedules + 1);
        appointment.setRescheduledAt(now);
        appointment.setRescheduledByName(actorUser != null ? actorUser.getFullName() : userEmail);
        appointment.setRescheduledByRole(actorRole != null ? actorRole : (actorUser != null ? actorUser.getRole() : "patient"));

        Appointment saved = appointmentRepository.save(appointment);

        // Record lifecycle audit event
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("oldDate", oldDate.toString());
        metadata.put("oldTime", oldTime.toString());
        metadata.put("newDate", request.getNewDate().toString());
        metadata.put("newTime", request.getNewTime().toString());
        metadata.put("rescheduleCount", appointment.getRescheduleCount());
        metadata.put("maxReschedulesAllowed", maxAllowed);
        metadata.put("reason", request.getReason());

        recordLifecycleEvent(
                saved.getId(),
                tenantId,
                "RESCHEDULED",
                actorUser != null ? actorUser.getId() : null,
                actorUser != null ? actorUser.getFullName() : userEmail,
                actorRole != null ? actorRole : "patient",
                saved.getAppointmentStatus(),
                saved.getAppointmentStatus(),
                request.getReason(),
                metadata
        );

        // Notify patient and provider
        try {
            notificationService.notifyAppointmentRescheduled(saved, request.getNewDate(), request.getNewTime());
            emailService.sendAppointmentRescheduledEmail(saved, oldDate, oldTime);
        } catch (Exception e) {
            log.warn("Notification error after rescheduling: {}", e.getMessage());
        }

        return saved;
    }

    /**
     * Manually triggers or retries a refund for an appointment (e.g. by admin).
     */
    @Transactional
    public Appointment processManualRefund(Long appointmentId, String adminEmail) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found with id " + appointmentId));

        if (!"CANCELLED".equalsIgnoreCase(appointment.getAppointmentStatus())) {
            throw new IllegalStateException("Refunds can only be processed on cancelled appointments.");
        }

        if (appointment.getRefundAmount() == null || appointment.getRefundAmount() <= 0) {
            throw new IllegalStateException("Appointment has no eligible refund amount.");
        }

        if ("REFUNDED".equalsIgnoreCase(appointment.getRefundStatus())) {
            throw new IllegalStateException("Appointment has already been refunded.");
        }

        User adminUser = userRepository.findByEmail(adminEmail).orElse(null);

        appointment.setRefundStatus("PROCESSING");
        try {
            String refundTxId = null;
            if ("STRIPE".equalsIgnoreCase(appointment.getPaymentMethod())) {
                String ref = appointment.getGatewayPaymentRef() != null ? appointment.getGatewayPaymentRef() : appointment.getTransactionId();
                refundTxId = paymentService.executeStripeRefund(ref, appointment.getRefundAmount());
            } else if ("ESEWA".equalsIgnoreCase(appointment.getPaymentMethod())) {
                refundTxId = paymentService.executeEsewaRefund(appointment.getTransactionId(), appointment.getRefundAmount());
            } else {
                refundTxId = "MANUAL-CASH-REFUND-" + UUID.randomUUID().toString().substring(0, 8);
            }

            appointment.setRefundTransactionId(refundTxId);
            appointment.setRefundStatus(appointment.getRefundEligibilityPercentage() != null && appointment.getRefundEligibilityPercentage() >= 100.0 ? "REFUNDED" : "PARTIALLY_REFUNDED");
            appointment.setRefundedAt(LocalDateTime.now());
            appointment.setRefundFailureReason(null);

            // Synchronize commission settlement and provider payout
            try {
                commissionService.adjustSettlementForNoShowOrRefund(appointment.getId(), appointment.getRefundAmount() != null ? appointment.getRefundAmount() : 0.0, appointment.getAppointmentStatus());
            } catch (Exception commEx) {
                log.warn("Failed adjusting commission ledger on manual refund for appointment {}: {}", appointment.getId(), commEx.getMessage());
            }

            recordLifecycleEvent(
                    appointment.getId(),
                    appointment.getTenantId(),
                    "REFUND_PROCESSED",
                    adminUser != null ? adminUser.getId() : null,
                    adminUser != null ? adminUser.getFullName() : adminEmail,
                    "admin",
                    "PROCESSING",
                    appointment.getRefundStatus(),
                    "Manual refund processed by admin",
                    Map.of(
                            "refundAmount", appointment.getRefundAmount(),
                            "refundCurrency", appointment.getRefundCurrency(),
                            "refundTransactionId", refundTxId
                    )
            );
        } catch (Exception e) {
            log.error("Manual refund failed for appointment {}: {}", appointment.getId(), e.getMessage());
            appointment.setRefundStatus("FAILED");
            appointment.setRefundFailureReason(e.getMessage());

            recordLifecycleEvent(
                    appointment.getId(),
                    appointment.getTenantId(),
                    "REFUND_FAILED",
                    adminUser != null ? adminUser.getId() : null,
                    adminUser != null ? adminUser.getFullName() : adminEmail,
                    "admin",
                    "PROCESSING",
                    "FAILED",
                    e.getMessage(),
                    Map.of("error", e.getMessage())
            );
            throw new RuntimeException("Refund execution failed: " + e.getMessage());
        }

        return appointmentRepository.save(appointment);
    }

    /**
     * Get complete chronological lifecycle audit events for an appointment.
     * Automatically backfills and persists any missing lifecycle milestones (BOOKED, APPROVED/CONFIRMED,
     * CHECKED_IN, COMPLETED, CANCELLED, NO_SHOW) from the Appointment entity's own audit timestamps.
     */
    @Transactional
    public List<AppointmentLifecycleEvent> getLifecycleEvents(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        List<AppointmentLifecycleEvent> events = lifecycleEventRepository.findByAppointmentIdOrderByCreatedAtAsc(appointmentId);
        if (appointment == null) {
            return events;
        }

        boolean hasBooked = events.stream().anyMatch(e -> "BOOKED".equalsIgnoreCase(e.getEventType()));
        boolean hasApproved = events.stream().anyMatch(e -> "CONFIRMED".equalsIgnoreCase(e.getEventType()) || "APPROVED".equalsIgnoreCase(e.getEventType()));
        boolean hasCheckedIn = events.stream().anyMatch(e -> "CHECKED_IN".equalsIgnoreCase(e.getEventType()));
        boolean hasCompleted = events.stream().anyMatch(e -> "COMPLETED".equalsIgnoreCase(e.getEventType()));
        boolean hasCancelled = events.stream().anyMatch(e -> "CANCELLED".equalsIgnoreCase(e.getEventType()));
        boolean hasNoShow = events.stream().anyMatch(e -> "NO_SHOW".equalsIgnoreCase(e.getEventType()));

        List<AppointmentLifecycleEvent> toSave = new ArrayList<>();

        // 1. BOOKED Milestone
        if (!hasBooked) {
            LocalDateTime bookedTime = appointment.getBookedAt() != null ? appointment.getBookedAt() : appointment.getCreatedAt();
            if (bookedTime != null) {
                toSave.add(AppointmentLifecycleEvent.builder()
                        .appointmentId(appointment.getId())
                        .tenantId(appointment.getTenantId())
                        .eventType("BOOKED")
                        .actorId(appointment.getBookedByUserId())
                        .actorName(appointment.getBookedByName() != null ? appointment.getBookedByName() : appointment.getPatientName())
                        .actorRole(appointment.getBookedByRole() != null ? appointment.getBookedByRole() : "PATIENT")
                        .fromStatus(null)
                        .toStatus(appointment.getApprovedAt() != null ? "PENDING_APPROVAL" : appointment.getAppointmentStatus())
                        .reason("Appointment booked and payment verified (" + (appointment.getPaymentMethod() != null ? appointment.getPaymentMethod() : "ONLINE") + ")")
                        .createdAt(bookedTime)
                        .build());
            }
        }

        // 2. CONFIRMED / APPROVED Milestone
        if (!hasApproved && appointment.getApprovedAt() != null) {
            toSave.add(AppointmentLifecycleEvent.builder()
                    .appointmentId(appointment.getId())
                    .tenantId(appointment.getTenantId())
                    .eventType("CONFIRMED")
                    .actorId(appointment.getApprovedByUserId())
                    .actorName(appointment.getApprovedByName() != null ? appointment.getApprovedByName() : "Service Provider")
                    .actorRole(appointment.getApprovedByRole() != null ? appointment.getApprovedByRole() : "PROVIDER")
                    .fromStatus("PENDING_APPROVAL")
                    .toStatus("SCHEDULED")
                    .reason("Appointment approved and confirmed by service provider")
                    .createdAt(appointment.getApprovedAt())
                    .build());
        }

        // 3. CHECKED_IN Milestone
        if (!hasCheckedIn && appointment.getCheckedInAt() != null) {
            toSave.add(AppointmentLifecycleEvent.builder()
                    .appointmentId(appointment.getId())
                    .tenantId(appointment.getTenantId())
                    .eventType("CHECKED_IN")
                    .actorId(appointment.getCheckedInByUserId())
                    .actorName(appointment.getCheckedInByName() != null ? appointment.getCheckedInByName() : "Staff")
                    .actorRole(appointment.getCheckedInByRole() != null ? appointment.getCheckedInByRole() : "PROVIDER")
                    .fromStatus("SCHEDULED")
                    .toStatus("CHECKED_IN")
                    .reason("Client arrived and checked in")
                    .createdAt(appointment.getCheckedInAt())
                    .build());
        }

        // 4. COMPLETED Milestone
        if (!hasCompleted && appointment.getCompletedAt() != null) {
            toSave.add(AppointmentLifecycleEvent.builder()
                    .appointmentId(appointment.getId())
                    .tenantId(appointment.getTenantId())
                    .eventType("COMPLETED")
                    .actorId(appointment.getCompletedByUserId())
                    .actorName(appointment.getCompletedByName() != null ? appointment.getCompletedByName() : "Staff")
                    .actorRole(appointment.getCompletedByRole() != null ? appointment.getCompletedByRole() : "PROVIDER")
                    .fromStatus("CHECKED_IN")
                    .toStatus("COMPLETED")
                    .reason("Appointment service completed successfully")
                    .createdAt(appointment.getCompletedAt())
                    .build());
        }

        // 5. CANCELLED Milestone
        if (!hasCancelled && appointment.getCancelledAt() != null && "CANCELLED".equalsIgnoreCase(appointment.getAppointmentStatus())) {
            toSave.add(AppointmentLifecycleEvent.builder()
                    .appointmentId(appointment.getId())
                    .tenantId(appointment.getTenantId())
                    .eventType("CANCELLED")
                    .actorId(appointment.getCancelledByUserId())
                    .actorName(appointment.getCancelledByName() != null ? appointment.getCancelledByName() : "Staff")
                    .actorRole(appointment.getCancelledByRole() != null ? appointment.getCancelledByRole() : "STAFF")
                    .fromStatus("SCHEDULED")
                    .toStatus("CANCELLED")
                    .reason(appointment.getCancellationReason() != null ? appointment.getCancellationReason() : "Appointment cancelled")
                    .createdAt(appointment.getCancelledAt())
                    .build());
        }

        // 6. NO_SHOW Milestone
        if (!hasNoShow && (appointment.getNoShowAt() != null || "NO_SHOW".equalsIgnoreCase(appointment.getAppointmentStatus()))) {
            LocalDateTime noShowTime = appointment.getNoShowAt() != null ? appointment.getNoShowAt() : appointment.getUpdatedAt();
            toSave.add(AppointmentLifecycleEvent.builder()
                    .appointmentId(appointment.getId())
                    .tenantId(appointment.getTenantId())
                    .eventType("NO_SHOW")
                    .actorName(appointment.getNoShowMarkedByName() != null ? appointment.getNoShowMarkedByName() : "System")
                    .actorRole(appointment.getNoShowMarkedByRole() != null ? appointment.getNoShowMarkedByRole() : "SYSTEM")
                    .fromStatus("SCHEDULED")
                    .toStatus("NO_SHOW")
                    .reason("Appointment missed past grace period")
                    .createdAt(noShowTime != null ? noShowTime : LocalDateTime.now())
                    .build());
        }

        if (!toSave.isEmpty()) {
            lifecycleEventRepository.saveAll(toSave);
            events = lifecycleEventRepository.findByAppointmentIdOrderByCreatedAtAsc(appointmentId);
        }

        return events;
    }

    private void validateAccess(Appointment appointment, String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return; // Permit internal system calls
        }
        User user = userRepository.findByEmail(userEmail).orElse(null);
        if (user == null) {
            throw new RuntimeException("User not found: " + userEmail);
        }

        String role = user.getRole() != null ? user.getRole().toLowerCase() : "";
        if (role.equals("admin") || role.equals("super_admin") || role.equals("superadmin")) {
            return; // Admins have full access
        }

        if (role.equals("service_provider") || role.equals("provider")) {
            Long providerId = appointment.getProviderId();
            if (providerId != null && providerId.equals(user.getId())) {
                return; // Provider can access their own appointment
            }
        }

        // Patients / regular users
        boolean matchesBookedUser = appointment.getBookedByUserId() != null && appointment.getBookedByUserId().equals(user.getId());
        boolean matchesPatientEmail = appointment.getPatientEmail() != null && appointment.getPatientEmail().equalsIgnoreCase(userEmail);

        if (!matchesBookedUser && !matchesPatientEmail) {
            throw new RuntimeException("Unauthorized: You do not have permission to modify this appointment.");
        }
    }

    /**
     * Classifies an appointment as NO_SHOW (either manually by staff or automatically by system after grace period).
     * Calculates refund according to tenant's noShowRefundPercentage (default 0.0%), executes gateway refund if > 0%,
     * and automatically settles provider payout & platform commission.
     */
    @Transactional
    public Appointment markAsNoShow(Long appointmentId, String actorRole, String actorName, String reason) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found with id " + appointmentId));

        String oldStatus = appointment.getAppointmentStatus();
        if ("NO_SHOW".equalsIgnoreCase(oldStatus)) {
            log.info("Appointment {} is already marked as NO_SHOW", appointmentId);
            return appointment;
        }
        if ("COMPLETED".equalsIgnoreCase(oldStatus)) {
            throw new IllegalStateException("Cannot mark an appointment as No-Show if it has already been completed.");
        }
        if ("CANCELLED".equalsIgnoreCase(oldStatus)) {
            throw new IllegalStateException("Cannot mark a cancelled appointment as No-Show.");
        }

        TenantPolicyDTO policy = getTenantPolicy(appointment.getTenantId());
        LocalDateTime now = LocalDateTime.now();

        appointment.setAppointmentStatus("NO_SHOW");
        appointment.setNoShowAt(now);
        appointment.setNoShowMarkedByName(actorName != null ? actorName : "System Automated Scheduler");
        appointment.setNoShowMarkedByRole(actorRole != null ? actorRole : "SYSTEM");

        double refundPercentage = policy.getNoShowRefundPercentage() != null ? policy.getNoShowRefundPercentage() : 0.0;
        appointment.setRefundEligibilityPercentage(refundPercentage);

        String refundCurrency = "NPR";
        double baseAmount = 0.0;
        if ("STRIPE".equalsIgnoreCase(appointment.getPaymentMethod())) {
            refundCurrency = "USD";
            baseAmount = appointment.getChargedAmount() != null ? appointment.getChargedAmount() : 0.0;
        } else if ("ESEWA".equalsIgnoreCase(appointment.getPaymentMethod())) {
            refundCurrency = "NPR";
            baseAmount = appointment.getBasePriceNpr() != null ? appointment.getBasePriceNpr() : (appointment.getPrice() != null ? appointment.getPrice() : 0.0);
        } else {
            baseAmount = appointment.getPrice() != null ? appointment.getPrice() : 0.0;
        }

        double refundAmount = 0.0;
        boolean hasPaid = "SUCCESS".equalsIgnoreCase(appointment.getPaymentStatus()) || "PAID".equalsIgnoreCase(appointment.getPaymentStatus());
        if (hasPaid && refundPercentage > 0.0) {
            refundAmount = Math.round((baseAmount * (refundPercentage / 100.0)) * 100.0) / 100.0;
        }

        appointment.setRefundAmount(refundAmount);
        appointment.setRefundCurrency(refundCurrency);

        if (refundAmount <= 0.0 || !hasPaid) {
            appointment.setRefundStatus("NOT_ELIGIBLE");
        } else {
            if (Boolean.TRUE.equals(policy.getAutoProcessRefunds())) {
                appointment.setRefundStatus("PROCESSING");
                appointment.setRefundRequestedAt(now);
                try {
                    String refundTxId = null;
                    if ("STRIPE".equalsIgnoreCase(appointment.getPaymentMethod())) {
                        String ref = appointment.getGatewayPaymentRef() != null ? appointment.getGatewayPaymentRef() : appointment.getTransactionId();
                        refundTxId = paymentService.executeStripeRefund(ref, refundAmount);
                    } else if ("ESEWA".equalsIgnoreCase(appointment.getPaymentMethod())) {
                        refundTxId = paymentService.executeEsewaRefund(appointment.getTransactionId(), refundAmount);
                    }
                    appointment.setRefundTransactionId(refundTxId);
                    appointment.setRefundStatus(refundPercentage >= 100.0 ? "REFUNDED" : "PARTIALLY_REFUNDED");
                    appointment.setRefundedAt(LocalDateTime.now());
                } catch (Exception refundEx) {
                    log.error("Automatic No-Show refund processing failed for appointment {}: {}", appointment.getId(), refundEx.getMessage());
                    appointment.setRefundStatus("FAILED");
                    appointment.setRefundFailureReason(refundEx.getMessage());
                }
            } else {
                appointment.setRefundStatus("REFUND_REQUESTED");
                appointment.setRefundRequestedAt(now);
            }
        }

        // Adjust Financial Settlement & Commission Ledgers
        try {
            commissionService.adjustSettlementForNoShowOrRefund(appointment.getId(), refundAmount, "NO_SHOW");
        } catch (Exception commEx) {
            log.warn("Failed adjusting commission ledger for No-Show appointment {}: {}", appointment.getId(), commEx.getMessage());
        }

        Appointment saved = appointmentRepository.save(appointment);

        // Record lifecycle audit event
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("noShowRefundPercentage", refundPercentage);
        metadata.put("refundAmount", refundAmount);
        metadata.put("refundCurrency", refundCurrency);
        metadata.put("refundStatus", appointment.getRefundStatus());
        metadata.put("settlementStatus", appointment.getSettlementStatus());
        metadata.put("settlementAmount", appointment.getSettlementAmount());
        metadata.put("reason", reason != null ? reason : "Unattended past configured grace period.");

        recordLifecycleEvent(
                saved.getId(),
                saved.getTenantId(),
                "NO_SHOW",
                null,
                appointment.getNoShowMarkedByName(),
                appointment.getNoShowMarkedByRole(),
                oldStatus,
                "NO_SHOW",
                reason != null ? reason : "Marked as No-Show after grace period expired.",
                metadata
        );

        // If refund was processed, record secondary audit event
        if (refundAmount > 0 && ("REFUNDED".equalsIgnoreCase(appointment.getRefundStatus()) || "PARTIALLY_REFUNDED".equalsIgnoreCase(appointment.getRefundStatus()))) {
            Map<String, Object> refundMeta = new HashMap<>();
            refundMeta.put("amount", refundAmount);
            refundMeta.put("currency", refundCurrency);
            refundMeta.put("percentage", refundPercentage);
            refundMeta.put("transactionId", appointment.getRefundTransactionId());
            recordLifecycleEvent(
                    saved.getId(),
                    saved.getTenantId(),
                    "REFUND_PROCESSED",
                    null,
                    "Payment Gateway (" + appointment.getPaymentMethod() + ")",
                    "GATEWAY",
                    "PROCESSING",
                    appointment.getRefundStatus(),
                    "Processed No-Show refund according to organization policy",
                    refundMeta
            );
        }

        // Send notifications
        try {
            notificationService.notifyAppointmentNoShow(saved);
        } catch (Exception notifEx) {
            log.warn("Failed sending No-Show notifications for appointment {}: {}", saved.getId(), notifEx.getMessage());
        }

        return saved;
    }

    /**
     * Automated job that scans for unattended appointments past their organization's configured grace period.
     */
    @Transactional
    public int processAutomatedNoShows() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        List<String> activeStatuses = Arrays.asList("SCHEDULED", "PENDING_APPROVAL", "CONFIRMED", "PENDING");
        List<Appointment> candidates = appointmentRepository.findAll().stream()
                .filter(a -> a.getAppointmentStatus() != null && activeStatuses.contains(a.getAppointmentStatus().toUpperCase()))
                .filter(a -> a.getAppointmentDate() != null && !a.getAppointmentDate().isAfter(today))
                .collect(java.util.stream.Collectors.toList());

        int count = 0;
        for (Appointment appt : candidates) {
            try {
                TenantPolicyDTO policy = getTenantPolicy(appt.getTenantId());
                if (policy != null && Boolean.FALSE.equals(policy.getAutoClassifyNoShow())) {
                    continue; // Auto No-Show disabled for this tenant
                }

                int graceMinutes = (policy != null && policy.getNoShowGracePeriodMinutes() != null)
                        ? policy.getNoShowGracePeriodMinutes()
                        : 15;

                LocalTime apptTime = appt.getAppointmentTime() != null ? appt.getAppointmentTime() : LocalTime.of(0, 0);
                LocalDateTime scheduledEnd = LocalDateTime.of(appt.getAppointmentDate(), apptTime).plusMinutes(graceMinutes);

                if (now.isAfter(scheduledEnd)) {
                    markAsNoShow(
                            appt.getId(),
                            "SYSTEM",
                            "System Automated Scheduler",
                            "Appointment unattended " + graceMinutes + " minutes past scheduled time (Grace period expired)"
                    );
                    count++;
                }
            } catch (Exception e) {
                log.error("Error auto-processing No-Show for appointment {}: {}", appt.getId(), e.getMessage());
            }
        }

        if (count > 0) {
            log.info("Auto-classified {} missed appointments as NO_SHOW with grace period validation.", count);
        }
        return count;
    }

    public void recordLifecycleEvent(Long appointmentId, Long tenantId, String eventType,
                                      Long actorId, String actorName, String actorRole,
                                      String fromStatus, String toStatus, String reason,
                                      Map<String, Object> metadata) {
        try {
            String json = metadata != null ? objectMapper.writeValueAsString(metadata) : null;
            AppointmentLifecycleEvent event = AppointmentLifecycleEvent.builder()
                    .appointmentId(appointmentId)
                    .tenantId(tenantId)
                    .eventType(eventType)
                    .actorId(actorId)
                    .actorName(actorName)
                    .actorRole(actorRole)
                    .fromStatus(fromStatus)
                    .toStatus(toStatus)
                    .reason(reason)
                    .metadataJson(json)
                    .createdAt(LocalDateTime.now())
                    .build();
            lifecycleEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to record lifecycle event for appointment {}: {}", appointmentId, e.getMessage());
        }
    }
}
