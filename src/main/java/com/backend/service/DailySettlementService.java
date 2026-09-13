package com.backend.service;

import com.backend.dto.DailySettlementDTOs;
import com.backend.model.Appointment;
import com.backend.model.DailySettlement;
import com.backend.model.Tenant;
import com.backend.model.User;
import com.backend.repository.AppointmentRepository;
import com.backend.repository.DailySettlementRepository;
import com.backend.repository.ProviderProfileRepository;
import com.backend.repository.TenantRepository;
import com.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailySettlementService {

    private final DailySettlementRepository dailySettlementRepository;
    private final AppointmentRepository appointmentRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final CommissionService commissionService;
    private final AuditLogService auditLogService;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a");

    // =========================================================================
    // 1. SUPER ADMIN: GLOBAL SETTLEMENT MONITORING
    // =========================================================================

    public DailySettlementDTOs.SuperAdminSettlementOverview getSuperAdminOverview(
            LocalDate startDate, LocalDate endDate, Long filterTenantId, String statusFilter, String orgTypeFilter) {

        List<Tenant> tenants = tenantRepository.findAll();
        Map<Long, Tenant> tenantMap = tenants.stream().collect(Collectors.toMap(Tenant::getId, t -> t, (a, b) -> a));

        List<DailySettlementDTOs.Summary> allSummaries = new ArrayList<>();

        for (Tenant tenant : tenants) {
            if (filterTenantId != null && !filterTenantId.equals(tenant.getId())) {
                continue;
            }
            if (orgTypeFilter != null && !orgTypeFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(orgTypeFilter)) {
                if (tenant.getOrganizationType() == null || !tenant.getOrganizationType().equalsIgnoreCase(orgTypeFilter)) {
                    continue;
                }
            }

            List<DailySettlementDTOs.Summary> tenantSummaries = getSummariesForTenant(tenant, startDate, endDate);
            allSummaries.addAll(tenantSummaries);
        }

        // Apply Status filter if specified
        if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
            allSummaries = allSummaries.stream()
                    .filter(s -> statusFilter.equalsIgnoreCase(s.getSettlementStatus()))
                    .collect(Collectors.toList());
        }

        // Sort descending by date
        allSummaries.sort((a, b) -> b.getSettlementDate().compareTo(a.getSettlementDate()));

        double totalSettledRevenue = 0.0;
        double totalPlatformEarnings = 0.0;
        double totalGatewayFees = 0.0;
        long totalSettledBatches = 0;
        long pendingEscrowBatches = 0;

        for (DailySettlementDTOs.Summary s : allSummaries) {
            if ("SETTLED".equalsIgnoreCase(s.getSettlementStatus())) {
                totalSettledRevenue += s.getGrossRevenue() != null ? s.getGrossRevenue() : 0.0;
                totalPlatformEarnings += s.getPlatformCommission() != null ? s.getPlatformCommission() : 0.0;
                totalGatewayFees += s.getGatewayFees() != null ? s.getGatewayFees() : 0.0;
                totalSettledBatches++;
            } else {
                pendingEscrowBatches++;
            }
        }

        return DailySettlementDTOs.SuperAdminSettlementOverview.builder()
                .totalSettledRevenue(round(totalSettledRevenue))
                .totalPlatformEarnings(round(totalPlatformEarnings))
                .totalGatewayFees(round(totalGatewayFees))
                .totalSettledBatches(totalSettledBatches)
                .pendingEscrowBatches(pendingEscrowBatches)
                .settlements(allSummaries)
                .build();
    }

    // =========================================================================
    // 2. ORGANIZATION ADMIN: TENANT SETTLEMENT HUB
    // =========================================================================

    public DailySettlementDTOs.AdminSettlementOverview getAdminOverview(Long tenantId, LocalDate startDate, LocalDate endDate, String statusFilter) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

        List<DailySettlementDTOs.Summary> summaries = getSummariesForTenant(tenant, startDate, endDate);

        if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
            summaries = summaries.stream()
                    .filter(s -> statusFilter.equalsIgnoreCase(s.getSettlementStatus()))
                    .collect(Collectors.toList());
        }

        summaries.sort((a, b) -> b.getSettlementDate().compareTo(a.getSettlementDate()));

        double totalOrgNetProfit = 0.0;
        double totalProviderPayouts = 0.0;
        double totalPlatformFees = 0.0;
        double todayInEscrow = 0.0;
        long completedCount = 0;

        LocalDate today = LocalDate.now();

        for (DailySettlementDTOs.Summary s : summaries) {
            if ("SETTLED".equalsIgnoreCase(s.getSettlementStatus())) {
                totalOrgNetProfit += s.getOrgAdminPayout() != null ? s.getOrgAdminPayout() : 0.0;
                totalProviderPayouts += s.getProviderPayoutsTotal() != null ? s.getProviderPayoutsTotal() : 0.0;
                totalPlatformFees += s.getPlatformCommission() != null ? s.getPlatformCommission() : 0.0;
                completedCount++;
            } else if (today.equals(s.getSettlementDate())) {
                todayInEscrow += s.getRemainingOrgAmount() != null ? s.getRemainingOrgAmount() : 0.0;
            }
        }

        return DailySettlementDTOs.AdminSettlementOverview.builder()
                .totalOrgNetProfit(round(totalOrgNetProfit))
                .totalProviderPayouts(round(totalProviderPayouts))
                .totalPlatformFeesPaid(round(totalPlatformFees))
                .todayInEscrowRevenue(round(todayInEscrow))
                .completedSettlementsCount(completedCount)
                .settlements(summaries)
                .build();
    }

    // =========================================================================
    // 3. DAILY SETTLEMENT DETAIL & AUDIT BREAKDOWN
    // =========================================================================

    public DailySettlementDTOs.Detail getDailySettlementDetail(Long tenantId, LocalDate date) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

        Optional<DailySettlement> existingOpt = dailySettlementRepository.findByTenantIdAndSettlementDate(tenantId, date);

        DailySettlement settlement;
        List<Appointment> appointments;

        if (existingOpt.isPresent() && existingOpt.get().getIsLocked()) {
            // Locked historical settlement
            settlement = existingOpt.get();
            appointments = appointmentRepository.findByTenantId(tenantId).stream()
                    .filter(a -> settlement.getId().equals(a.getDailySettlementId()) || date.equals(a.getSettlementBatchDate()))
                    .collect(Collectors.toList());
        } else {
            // Live unfinalized or in-escrow day: dynamically aggregate
            appointments = getEligibleAppointmentsForDate(tenantId, date);
            settlement = calculateSettlementSnapshot(tenant, date, appointments, existingOpt.orElse(null));
        }

        DailySettlementDTOs.Summary summary = mapToSummaryDTO(settlement);

        // Build Provider shares breakdown
        Map<Long, List<Appointment>> byProvider = appointments.stream()
                .filter(a -> a.getProviderId() != null)
                .collect(Collectors.groupingBy(Appointment::getProviderId));

        List<DailySettlementDTOs.ProviderShare> providerShares = new ArrayList<>();
        for (Map.Entry<Long, List<Appointment>> entry : byProvider.entrySet()) {
            Long provId = entry.getKey();
            List<Appointment> provApps = entry.getValue();

            User provUser = userRepository.findById(provId).orElse(null);
            String provName = provUser != null ? provUser.getFullName() : ("Provider #" + provId);
            String specialty = provUser != null
                    ? providerProfileRepository.findByUser(provUser).map(com.backend.model.ProviderProfile::getPrimarySpecialty).orElse("General")
                    : "General";

            double provRate = commissionService.resolveCommissionRate(provId, tenantId);
            double provGross = 0.0;
            double provPayout = 0.0;
            double provOrgShare = 0.0;

            for (Appointment a : provApps) {
                TierCalculation tier = calculateAppointmentTier(a, provRate, settlement.getPlatformCommissionRate());
                provGross += tier.gross;
                provPayout += tier.providerPayout;
                provOrgShare += tier.orgAdminPayout;
            }

            providerShares.add(DailySettlementDTOs.ProviderShare.builder()
                    .providerId(provId)
                    .providerName(provName)
                    .specialtyOrRole(specialty)
                    .appointmentsCount(provApps.size())
                    .attributedGross(round(provGross))
                    .commissionRate(provRate)
                    .netProviderPayout(round(provPayout))
                    .netOrgShare(round(provOrgShare))
                    .build());
        }

        // Build line item appointments
        List<DailySettlementDTOs.AppointmentItem> appointmentItems = new ArrayList<>();
        for (Appointment a : appointments) {
            double provRate = commissionService.resolveCommissionRate(a.getProviderId(), tenantId);
            TierCalculation tier = calculateAppointmentTier(a, provRate, settlement.getPlatformCommissionRate());

            User provUser = a.getProviderId() != null ? userRepository.findById(a.getProviderId()).orElse(null) : null;
            String provName = provUser != null ? provUser.getFullName() : (a.getDoctorName() != null ? a.getDoctorName() : "Unassigned");

            appointmentItems.add(DailySettlementDTOs.AppointmentItem.builder()
                    .appointmentId(a.getId())
                    .time(a.getAppointmentTime() != null ? a.getAppointmentTime().format(TIME_FORMATTER) : "--:--")
                    .customerName(a.getPatientName() != null ? a.getPatientName() : "Anonymous")
                    .customerPhone(a.getPatientPhone())
                    .serviceName(a.getServiceName() != null ? a.getServiceName() : "Standard Service")
                    .providerName(provName)
                    .paymentMethod(a.getPaymentMethod() != null ? a.getPaymentMethod() : "ESEWA")
                    .gross(tier.gross)
                    .refund(tier.refund)
                    .netRetained(tier.netRetained)
                    .gatewayFee(tier.gatewayFee)
                    .platformFee(tier.platformFee)
                    .remainingOrg(tier.remainingOrg)
                    .providerPayout(tier.providerPayout)
                    .orgAdminPayout(tier.orgAdminPayout)
                    .status(a.getPaymentStatus() != null ? a.getPaymentStatus() : "SUCCESS")
                    .settlementStatus(settlement.getSettlementStatus())
                    .build());
        }

        boolean eligibleForFinalize = !Boolean.TRUE.equals(settlement.getIsLocked()) && !appointments.isEmpty();

        return DailySettlementDTOs.Detail.builder()
                .summary(summary)
                .providerShares(providerShares)
                .appointments(appointmentItems)
                .isEligibleForFinalization(eligibleForFinalize)
                .build();
    }

    // =========================================================================
    // 4. SERVICE PROVIDER: INDIVIDUAL SETTLEMENT HUB
    // =========================================================================

    public DailySettlementDTOs.ProviderSettlementOverview getProviderOverview(Long providerUserId, Long tenantId) {
        User provider = userRepository.findById(providerUserId)
                .orElseThrow(() -> new RuntimeException("Provider user not found: " + providerUserId));

        double activeRate = commissionService.resolveCommissionRate(providerUserId, tenantId);

        List<Appointment> allProviderApps = appointmentRepository.findByTenantId(tenantId).stream()
                .filter(a -> providerUserId.equals(a.getProviderId()))
                .filter(this::isEligibleForSettlement)
                .collect(Collectors.toList());

        // Group by settlement batch date (or appointment date)
        Map<LocalDate, List<Appointment>> byDate = allProviderApps.stream()
                .collect(Collectors.groupingBy(a -> a.getSettlementBatchDate() != null ? a.getSettlementBatchDate() : a.getAppointmentDate()));

        List<DailySettlementDTOs.ProviderDailyView> dailyViews = new ArrayList<>();
        LocalDate today = LocalDate.now();

        double totalNetEarningsAllTime = 0.0;
        double thisMonthNet = 0.0;
        double todayProjected = 0.0;

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        String orgName = tenant != null ? tenant.getOrganizationName() : "Organization";
        String orgType = tenant != null ? tenant.getOrganizationType() : "CLINIC";

        for (Map.Entry<LocalDate, List<Appointment>> entry : byDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<Appointment> dayApps = entry.getValue();

            Optional<DailySettlement> dsOpt = dailySettlementRepository.findByTenantIdAndSettlementDate(tenantId, date);
            boolean isLocked = dsOpt.isPresent() && Boolean.TRUE.equals(dsOpt.get().getIsLocked());
            String status = isLocked ? "SETTLED" : "IN_ESCROW";
            double platformRate = dsOpt.map(DailySettlement::getPlatformCommissionRate).orElse(10.0);

            double dayGross = 0.0;
            double dayRetained = 0.0;
            double dayNetPayout = 0.0;

            List<DailySettlementDTOs.AppointmentItem> items = new ArrayList<>();
            for (Appointment a : dayApps) {
                TierCalculation tier = calculateAppointmentTier(a, activeRate, platformRate);
                dayGross += tier.gross;
                dayRetained += tier.netRetained;
                dayNetPayout += tier.providerPayout;

                items.add(DailySettlementDTOs.AppointmentItem.builder()
                        .appointmentId(a.getId())
                        .time(a.getAppointmentTime() != null ? a.getAppointmentTime().format(TIME_FORMATTER) : "--:--")
                        .customerName(a.getPatientName())
                        .customerPhone(a.getPatientPhone())
                        .serviceName(a.getServiceName())
                        .providerName(provider.getFullName())
                        .paymentMethod(a.getPaymentMethod())
                        .gross(tier.gross)
                        .refund(tier.refund)
                        .netRetained(tier.netRetained)
                        .gatewayFee(tier.gatewayFee)
                        .platformFee(tier.platformFee)
                        .remainingOrg(tier.remainingOrg)
                        .providerPayout(tier.providerPayout)
                        .orgAdminPayout(tier.orgAdminPayout)
                        .status(a.getPaymentStatus())
                        .settlementStatus(status)
                        .build());
            }

            dayNetPayout = round(dayNetPayout);

            if (isLocked) {
                totalNetEarningsAllTime += dayNetPayout;
            }
            if (date.getMonth() == today.getMonth() && date.getYear() == today.getYear()) {
                thisMonthNet += dayNetPayout;
            }
            if (today.equals(date)) {
                todayProjected += dayNetPayout;
            }

            dailyViews.add(DailySettlementDTOs.ProviderDailyView.builder()
                    .date(date)
                    .tenantId(tenantId)
                    .organizationName(orgName)
                    .organizationType(orgType)
                    .appointmentsCount(dayApps.size())
                    .attributedGross(round(dayGross))
                    .netRetained(round(dayRetained))
                    .commissionRate(activeRate)
                    .netProviderPayout(dayNetPayout)
                    .status(status)
                    .isLocked(isLocked)
                    .settledAt(dsOpt.filter(d -> d.getSettledAt() != null).map(d -> d.getSettledAt().format(DATE_TIME_FORMATTER)).orElse(null))
                    .appointments(items)
                    .build());
        }

        dailyViews.sort((a, b) -> b.getDate().compareTo(a.getDate()));

        return DailySettlementDTOs.ProviderSettlementOverview.builder()
                .totalNetEarningsAllTime(round(totalNetEarningsAllTime))
                .thisMonthNetSettlement(round(thisMonthNet))
                .todayProjectedEarnings(round(todayProjected))
                .activeCommissionRate(activeRate)
                .settlements(dailyViews)
                .build();
    }

    public DailySettlementDTOs.ProviderDailyView getProviderDailySettlementDetail(Long providerUserId, Long tenantId, LocalDate date) {
        User provider = userRepository.findById(providerUserId)
                .orElseThrow(() -> new RuntimeException("Provider user not found: " + providerUserId));

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        String orgName = tenant != null ? tenant.getOrganizationName() : "Organization";
        String orgType = tenant != null ? tenant.getOrganizationType() : "CLINIC";

        double activeRate = commissionService.resolveCommissionRate(providerUserId, tenantId);

        Optional<DailySettlement> dsOpt = dailySettlementRepository.findByTenantIdAndSettlementDate(tenantId, date);
        boolean isLocked = dsOpt.isPresent() && Boolean.TRUE.equals(dsOpt.get().getIsLocked());
        String status = isLocked ? "SETTLED" : "IN_ESCROW";
        double platformRate = dsOpt.map(DailySettlement::getPlatformCommissionRate).orElse(10.0);

        List<Appointment> dayApps;
        if (isLocked) {
            DailySettlement settlement = dsOpt.get();
            dayApps = appointmentRepository.findByTenantId(tenantId).stream()
                    .filter(a -> providerUserId.equals(a.getProviderId()))
                    .filter(a -> settlement.getId().equals(a.getDailySettlementId()) || date.equals(a.getSettlementBatchDate()) || (a.getDailySettlementId() == null && date.equals(a.getAppointmentDate())))
                    .filter(this::isEligibleForSettlement)
                    .collect(Collectors.toList());
        } else {
            dayApps = getEligibleAppointmentsForDate(tenantId, date).stream()
                    .filter(a -> providerUserId.equals(a.getProviderId()))
                    .collect(Collectors.toList());
        }

        double dayGross = 0.0;
        double dayRetained = 0.0;
        double dayNetPayout = 0.0;

        List<DailySettlementDTOs.AppointmentItem> items = new ArrayList<>();
        for (Appointment a : dayApps) {
            TierCalculation tier = calculateAppointmentTier(a, activeRate, platformRate);
            dayGross += tier.gross;
            dayRetained += tier.netRetained;
            dayNetPayout += tier.providerPayout;

            items.add(DailySettlementDTOs.AppointmentItem.builder()
                    .appointmentId(a.getId())
                    .time(a.getAppointmentTime() != null ? a.getAppointmentTime().format(TIME_FORMATTER) : "--:--")
                    .customerName(a.getPatientName() != null ? a.getPatientName() : "Anonymous")
                    .customerPhone(a.getPatientPhone())
                    .serviceName(a.getServiceName() != null ? a.getServiceName() : "Standard Service")
                    .providerName(provider.getFullName())
                    .paymentMethod(a.getPaymentMethod() != null ? a.getPaymentMethod() : "ESEWA")
                    .gross(tier.gross)
                    .refund(tier.refund)
                    .netRetained(tier.netRetained)
                    .gatewayFee(tier.gatewayFee)
                    .platformFee(tier.platformFee)
                    .remainingOrg(tier.remainingOrg)
                    .providerPayout(tier.providerPayout)
                    .orgAdminPayout(tier.orgAdminPayout)
                    .status(a.getPaymentStatus() != null ? a.getPaymentStatus() : "SUCCESS")
                    .settlementStatus(status)
                    .build());
        }

        return DailySettlementDTOs.ProviderDailyView.builder()
                .date(date)
                .tenantId(tenantId)
                .organizationName(orgName)
                .organizationType(orgType)
                .appointmentsCount(dayApps.size())
                .attributedGross(round(dayGross))
                .netRetained(round(dayRetained))
                .commissionRate(activeRate)
                .netProviderPayout(round(dayNetPayout))
                .status(status)
                .isLocked(isLocked)
                .settledAt(dsOpt.filter(d -> d.getSettledAt() != null).map(d -> d.getSettledAt().format(DATE_TIME_FORMATTER)).orElse(null))
                .appointments(items)
                .build();
    }

    // =========================================================================
    // 5. FINALIZE & LOCK DAILY SETTLEMENT (AUDIT GUARANTEE)
    // =========================================================================

    @Transactional
    public DailySettlementDTOs.Summary finalizeDailySettlement(Long tenantId, LocalDate date, String adminEmail) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found: " + adminEmail));

        Optional<DailySettlement> existingOpt = dailySettlementRepository.findByTenantIdAndSettlementDate(tenantId, date);
        if (existingOpt.isPresent() && Boolean.TRUE.equals(existingOpt.get().getIsLocked())) {
            throw new RuntimeException("Daily settlement for " + date + " has already been finalized and locked for audit.");
        }

        List<Appointment> eligibleApps = getEligibleAppointmentsForDate(tenantId, date);
        if (eligibleApps.isEmpty()) {
            throw new RuntimeException("No eligible completed appointments found to settle for " + date);
        }

        DailySettlement settlement = calculateSettlementSnapshot(tenant, date, eligibleApps, existingOpt.orElse(null));
        settlement.setSettlementStatus("SETTLED");
        settlement.setIsLocked(true);
        settlement.setSettledAt(LocalDateTime.now());
        settlement.setSettledBy(admin.getFullName() + " (" + adminEmail + ")");
        settlement.setNotes("Final revenue split from completed appointments and payments for " + date + ". Locked for audit.");

        DailySettlement saved = dailySettlementRepository.save(settlement);

        // Attach each appointment to this finalized settlement and freeze calculations
        for (Appointment a : eligibleApps) {
            double provRate = commissionService.resolveCommissionRate(a.getProviderId(), tenantId);
            TierCalculation tier = calculateAppointmentTier(a, provRate, saved.getPlatformCommissionRate());

            a.setDailySettlementId(saved.getId());
            a.setSettlementBatchDate(date);
            a.setSettlementStatus("SETTLED");
            a.setSettlementAmount(tier.providerPayout);
            a.setOrgSettlementAmount(tier.orgAdminPayout);
            a.setRemainingOrgAmount(tier.remainingOrg);
            a.setNetRetainedAmount(tier.netRetained);
            a.setGatewayFeeAmount(tier.gatewayFee);
            if ("IN ESCROW".equalsIgnoreCase(a.getPaymentStatus())) {
                a.setPaymentStatus("SETTLED");
            }
            appointmentRepository.save(a);
        }

        auditLogService.logAction(admin, "Finalized and locked Daily Settlement for " + tenant.getOrganizationName() + " on date: " + date, "127.0.0.1");
        log.info("Daily settlement locked for tenant {} on date {}: Total Rev={}, Provider Share={}, Org Profit={}",
                tenantId, date, saved.getGrossRevenue(), saved.getProviderPayoutsTotal(), saved.getOrgAdminPayout());

        return mapToSummaryDTO(saved);
    }

    // =========================================================================
    // HELPER: CARRY-FORWARD & ELIGIBILITY RESOLVER
    // =========================================================================

    public List<Appointment> getEligibleAppointmentsForDate(Long tenantId, LocalDate date) {
        List<Appointment> all = appointmentRepository.findByTenantId(tenantId);
        List<Appointment> result = new ArrayList<>();

        for (Appointment a : all) {
            if (!isEligibleForSettlement(a)) {
                continue;
            }

            // If already bound to a settlement
            if (a.getDailySettlementId() != null) {
                if (date.equals(a.getSettlementBatchDate())) {
                    result.add(a);
                }
                continue;
            }

            // Check appointment date
            LocalDate appDate = a.getAppointmentDate();
            if (date.equals(appDate)) {
                result.add(a);
            } else if (appDate != null && appDate.isBefore(date)) {
                // CARRY FORWARD CHECK:
                // If this appointment was from a past date whose settlement was ALREADY locked,
                // and this appointment was not included, it rolls forward into the current open batch!
                Optional<DailySettlement> pastSettlement = dailySettlementRepository.findByTenantIdAndSettlementDate(tenantId, appDate);
                if (pastSettlement.isPresent() && Boolean.TRUE.equals(pastSettlement.get().getIsLocked())) {
                    // Past batch is locked, so this unattached appointment carries forward to the current active date!
                    if (date.equals(LocalDate.now())) {
                        result.add(a);
                    }
                }
            }
        }

        return result;
    }

    private boolean isEligibleForSettlement(Appointment a) {
        if (a == null) return false;

        String apptStatus = a.getAppointmentStatus() != null ? a.getAppointmentStatus().toUpperCase() : "";
        // Strictly exclude REJECTED and PENDING_APPROVAL appointments from daily settlements
        if ("REJECTED".equals(apptStatus) || "PENDING_APPROVAL".equals(apptStatus) || "EXPIRED".equals(apptStatus)) {
            return false;
        }

        String payStatus = a.getPaymentStatus() != null ? a.getPaymentStatus().toUpperCase() : "";
        if ("REFUNDED".equals(payStatus) || "VOIDED".equals(payStatus) || "FAILED".equals(payStatus)) {
            return false;
        }

        boolean paidOrEscrow = "SUCCESS".equals(payStatus) || "PAID".equals(payStatus) || "SETTLED".equals(payStatus) 
                || "IN ESCROW".equals(payStatus) || "PARTIALLY_REFUNDED".equals(payStatus) || "NO_SHOW_SETTLED".equals(payStatus);

        if (!paidOrEscrow) {
            return false;
        }

        // Exclude 100% full refund with 0 net retained
        double gross = a.getPrice() != null ? a.getPrice() : 0.0;
        double refund = a.getRefundAmount() != null ? a.getRefundAmount() : 0.0;
        if (refund >= gross && gross > 0) {
            return false;
        }

        return true;
    }

    private List<DailySettlementDTOs.Summary> getSummariesForTenant(Tenant tenant, LocalDate startDate, LocalDate endDate) {
        Long tenantId = tenant.getId();
        List<DailySettlement> stored = dailySettlementRepository.findByTenantIdOrderBySettlementDateDesc(tenantId);
        Map<LocalDate, DailySettlement> storedByDate = stored.stream()
                .collect(Collectors.toMap(DailySettlement::getSettlementDate, d -> d, (a, b) -> a));

        // Find all distinct dates with appointments
        List<Appointment> allApps = appointmentRepository.findByTenantId(tenantId);
        Set<LocalDate> dates = new TreeSet<>(Collections.reverseOrder());

        for (Appointment a : allApps) {
            if (isEligibleForSettlement(a)) {
                LocalDate d = a.getSettlementBatchDate() != null ? a.getSettlementBatchDate() : a.getAppointmentDate();
                if (d != null) {
                    dates.add(d);
                }
            }
        }

        // Always include today
        dates.add(LocalDate.now());

        // Include any stored dates
        dates.addAll(storedByDate.keySet());

        List<DailySettlementDTOs.Summary> result = new ArrayList<>();

        for (LocalDate date : dates) {
            if (startDate != null && date.isBefore(startDate)) continue;
            if (endDate != null && date.isAfter(endDate)) continue;

            DailySettlement ds = storedByDate.get(date);
            if (ds != null && Boolean.TRUE.equals(ds.getIsLocked())) {
                result.add(mapToSummaryDTO(ds));
            } else {
                // Dynamically calculate live projected snapshot
                List<Appointment> dayApps = getEligibleAppointmentsForDate(tenantId, date);
                DailySettlement liveSnapshot = calculateSettlementSnapshot(tenant, date, dayApps, ds);
                result.add(mapToSummaryDTO(liveSnapshot));
            }
        }

        return result;
    }

    private DailySettlement calculateSettlementSnapshot(Tenant tenant, LocalDate date, List<Appointment> appointments, DailySettlement existing) {
        double platformRate = commissionService.getCurrentCommissionRate();

        double gross = 0.0;
        double refunds = 0.0;
        double netRetained = 0.0;
        double platformFee = 0.0;
        double gatewayFees = 0.0;
        double remainingOrg = 0.0;
        double providerPayouts = 0.0;
        double orgAdminPayout = 0.0;

        for (Appointment a : appointments) {
            double provRate = commissionService.resolveCommissionRate(a.getProviderId(), tenant.getId());
            TierCalculation tier = calculateAppointmentTier(a, provRate, platformRate);

            gross += tier.gross;
            refunds += tier.refund;
            netRetained += tier.netRetained;
            platformFee += tier.platformFee;
            gatewayFees += tier.gatewayFee;
            remainingOrg += tier.remainingOrg;
            providerPayouts += tier.providerPayout;
            orgAdminPayout += tier.orgAdminPayout;
        }

        DailySettlement s = existing != null ? existing : new DailySettlement();
        s.setTenantId(tenant.getId());
        s.setTenantName(tenant.getOrganizationName());
        s.setOrganizationType(tenant.getOrganizationType() != null ? tenant.getOrganizationType() : "CLINIC");
        s.setSettlementDate(date);
        s.setTotalAppointments(appointments.size());
        s.setGrossRevenue(round(gross));
        s.setTotalRefunds(round(refunds));
        s.setNetRetained(round(netRetained));
        s.setPlatformCommissionRate(platformRate);
        s.setPlatformCommission(round(platformFee));
        s.setGatewayFees(round(gatewayFees));
        s.setRemainingOrgAmount(round(remainingOrg));
        s.setProviderPayoutsTotal(round(providerPayouts));
        s.setOrgAdminPayout(round(orgAdminPayout));
        if (s.getSettlementStatus() == null) {
            s.setSettlementStatus("IN_ESCROW");
        }
        if (s.getIsLocked() == null) {
            s.setIsLocked(false);
        }

        return s;
    }

    private TierCalculation calculateAppointmentTier(Appointment a, double providerRate, double platformRate) {
        String apptStatus = a.getAppointmentStatus() != null ? a.getAppointmentStatus().toUpperCase() : "";
        String payStatus = a.getPaymentStatus() != null ? a.getPaymentStatus().toUpperCase() : "";
        if ("REJECTED".equals(apptStatus) || "REFUNDED".equals(payStatus) || "VOIDED".equals(payStatus)) {
            return new TierCalculation(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        boolean isStripe = "STRIPE".equalsIgnoreCase(a.getPaymentMethod());
        double gross = (isStripe && a.getChargedAmount() != null)
                ? a.getChargedAmount()
                : (a.getPrice() != null ? a.getPrice() : 0.0);

        double refund = a.getRefundAmount() != null ? a.getRefundAmount() : 0.0;
        double netRetained = Math.max(0.0, round(gross - refund));

        double platformFee = round(netRetained * (platformRate / 100.0));
        double gatewayFee = a.getGatewayFeeAmount() != null
                ? a.getGatewayFeeAmount()
                : commissionService.calculateGatewayFee(a.getPaymentMethod(), netRetained);
        gatewayFee = round(gatewayFee);

        double remainingOrg = Math.max(0.0, round(netRetained - platformFee - gatewayFee));
        double providerPayout = round(remainingOrg * (providerRate / 100.0));
        double orgAdminPayout = Math.max(0.0, round(remainingOrg - providerPayout));

        return new TierCalculation(gross, refund, netRetained, platformFee, gatewayFee, remainingOrg, providerPayout, orgAdminPayout);
    }

    private DailySettlementDTOs.Summary mapToSummaryDTO(DailySettlement s) {
        return DailySettlementDTOs.Summary.builder()
                .id(s.getId())
                .tenantId(s.getTenantId())
                .tenantName(s.getTenantName())
                .organizationType(s.getOrganizationType())
                .settlementDate(s.getSettlementDate())
                .totalAppointments(s.getTotalAppointments())
                .grossRevenue(s.getGrossRevenue())
                .totalRefunds(s.getTotalRefunds())
                .netRetained(s.getNetRetained())
                .platformCommissionRate(s.getPlatformCommissionRate())
                .platformCommission(s.getPlatformCommission())
                .gatewayFees(s.getGatewayFees())
                .remainingOrgAmount(s.getRemainingOrgAmount())
                .providerPayoutsTotal(s.getProviderPayoutsTotal())
                .orgAdminPayout(s.getOrgAdminPayout())
                .settlementStatus(s.getSettlementStatus() != null ? s.getSettlementStatus() : "IN_ESCROW")
                .isLocked(Boolean.TRUE.equals(s.getIsLocked()))
                .settledAt(s.getSettledAt() != null ? s.getSettledAt().format(DATE_TIME_FORMATTER) : null)
                .settledBy(s.getSettledBy())
                .build();
    }

    private static double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    private static class TierCalculation {
        final double gross;
        final double refund;
        final double netRetained;
        final double platformFee;
        final double gatewayFee;
        final double remainingOrg;
        final double providerPayout;
        final double orgAdminPayout;

        TierCalculation(double gross, double refund, double netRetained, double platformFee,
                        double gatewayFee, double remainingOrg, double providerPayout, double orgAdminPayout) {
            this.gross = gross;
            this.refund = refund;
            this.netRetained = netRetained;
            this.platformFee = platformFee;
            this.gatewayFee = gatewayFee;
            this.remainingOrg = remainingOrg;
            this.providerPayout = providerPayout;
            this.orgAdminPayout = orgAdminPayout;
        }
    }
}
