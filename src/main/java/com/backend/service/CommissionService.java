package com.backend.service;

import com.backend.dto.AppointmentCommissionDTO;
import com.backend.model.Appointment;
import com.backend.model.AppointmentCommission;
import com.backend.model.PlatformSetting;
import com.backend.model.Tenant;
import com.backend.repository.AppointmentCommissionRepository;
import com.backend.repository.AppointmentRepository;
import com.backend.repository.PlatformSettingRepository;
import com.backend.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionService {

    private final AppointmentCommissionRepository appointmentCommissionRepository;
    private final PlatformSettingRepository platformSettingRepository;
    private final AppointmentRepository appointmentRepository;
    private final TenantRepository tenantRepository;
    private final com.backend.repository.ProviderProfileRepository providerProfileRepository;

    /**
     * Dynamically resolves the applicable commission rate.
     * Hierarchy:
     * 1. Provider-specific custom commission rate (if configured on ProviderProfile).
     * 2. Organization default commission rate (if configured on Tenant).
     * 3. Database Platform Setting baseline (never hardcoded in business formulas).
     */
    public double resolveCommissionRate(Long providerUserId, Long tenantId) {
        if (providerUserId != null) {
            var profileOpt = providerProfileRepository.findByUserId(providerUserId);
            if (profileOpt.isPresent() && profileOpt.get().getCommissionRate() != null && profileOpt.get().getCommissionRate() >= 0.0) {
                return profileOpt.get().getCommissionRate();
            }
        }
        if (tenantId != null) {
            var tenantOpt = tenantRepository.findById(tenantId);
            if (tenantOpt.isPresent() && tenantOpt.get().getDefaultCommissionRate() != null && tenantOpt.get().getDefaultCommissionRate() >= 0.0) {
                return tenantOpt.get().getDefaultCommissionRate();
            }
        }
        return getCurrentCommissionRate();
    }

    /**
     * Retrieves currently configured platform appointment commission rate baseline.
     */
    public double getCurrentCommissionRate() {
        return platformSettingRepository.findAll().stream()
                .findFirst()
                .map(PlatformSetting::getAppointmentCommissionRate)
                .filter(r -> r != null && r >= 0.0)
                .orElse(10.0);
    }

    /**
     * Updates the platform appointment commission rate (Super Admin only).
     */
    @Transactional
    public double updateCommissionRate(double newRate) {
        if (newRate < 0.0 || newRate > 100.0) {
            throw new RuntimeException("Commission rate must be between 0% and 100%.");
        }
        PlatformSetting setting = platformSettingRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> PlatformSetting.builder().build());

        setting.setAppointmentCommissionRate(newRate);
        platformSettingRepository.save(setting);
        log.info("Platform appointment commission rate updated to {}%", newRate);
        return newRate;
    }

    /**
     * Records or updates immutable platform commission on appointment payment or completion.
     */
    @Transactional
    public AppointmentCommission recordAppointmentCommission(Appointment appointment, String paymentStatus) {
        if (appointment == null || appointment.getId() == null) {
            return null;
        }

        // Check if commission was already recorded for this appointment
        AppointmentCommission commission = appointmentCommissionRepository
                .findByAppointmentId(appointment.getId())
                .orElse(null);

        boolean isStripe = "STRIPE".equalsIgnoreCase(appointment.getPaymentMethod());
        String currency = appointment.getChargedCurrency() != null ? appointment.getChargedCurrency() : (isStripe ? "USD" : "NPR");
        double gross = (isStripe && appointment.getChargedAmount() != null)
                ? appointment.getChargedAmount()
                : (appointment.getPrice() != null ? appointment.getPrice() : 0.0);
        double baseNpr = appointment.getBasePriceNpr() != null
                ? appointment.getBasePriceNpr()
                : (appointment.getPrice() != null ? appointment.getPrice() : 0.0);
        String paymentMethod = appointment.getPaymentMethod() != null ? appointment.getPaymentMethod() : (isStripe ? "STRIPE" : "ESEWA");
        Double exRate = appointment.getExchangeRate() != null ? appointment.getExchangeRate() : 1.0;

        // 1. Net Retained Amount (initial booking has 0 refund)
        double refundAmt = 0.0;
        double netRetained = Math.max(0.0, Math.round(gross * 100.0) / 100.0);

        // 2. Platform Commission (Platform SaaS fee on Net Retained Amount)
        double platformRate = getCurrentCommissionRate();
        double platformCommAmt = Math.round((netRetained * (platformRate / 100.0)) * 100.0) / 100.0;

        // 3. Applicable Gateway Fees
        double gatewayFee = calculateGatewayFee(paymentMethod, netRetained);

        // 4. Gross Remaining Organization Amount (Net Retained - Platform Fee - Gateway Fee)
        double remainingOrg = Math.max(0.0, Math.round((netRetained - platformCommAmt - gatewayFee) * 100.0) / 100.0);

        // 5. Net Service Provider (Admin configured Provider Rate % on Gross Remaining Org Amount)
        double providerRate = resolveCommissionRate(appointment.getProviderId(), appointment.getTenantId());
        double providerPayout = Math.round((remainingOrg * (providerRate / 100.0)) * 100.0) / 100.0;

        // 6. Net Organization Admin (Remaining Org Amount - Net Service Provider)
        double orgAdminPayout = Math.max(0.0, Math.round((remainingOrg - providerPayout) * 100.0) / 100.0);

        String status = paymentStatus != null ? paymentStatus.toUpperCase() : "SUCCESS";

        if (commission == null) {
            commission = AppointmentCommission.builder()
                    .appointmentId(appointment.getId())
                    .tenantId(appointment.getTenantId())
                    .transactionId(appointment.getTransactionId() != null ? appointment.getTransactionId() : ("COMM-TXN-" + appointment.getId()))
                    .grossAmount(gross)
                    .currency(currency)
                    .baseAmountNpr(baseNpr)
                    .exchangeRate(exRate)
                    .refundDeductionAmount(refundAmt)
                    .netRetainedAmount(netRetained)
                    .platformCommissionRate(platformRate)
                    .commissionAmount(platformCommAmt)
                    .gatewayFeeAmount(gatewayFee)
                    .remainingOrgAmount(remainingOrg)
                    .commissionRate(providerRate)
                    .providerPayout(providerPayout)
                    .orgAdminPayout(orgAdminPayout)
                    .paymentStatus(status)
                    .settlementStatus("SETTLED")
                    .paymentMethod(paymentMethod)
                    .paymentDate(LocalDateTime.now())
                    .build();
        } else {
            commission.setPaymentStatus(status);
            commission.setCurrency(currency);
            commission.setBaseAmountNpr(baseNpr);
            commission.setExchangeRate(exRate);
            commission.setNetRetainedAmount(netRetained);
            commission.setPlatformCommissionRate(platformRate);
            commission.setCommissionAmount(platformCommAmt);
            commission.setGatewayFeeAmount(gatewayFee);
            commission.setRemainingOrgAmount(remainingOrg);
            commission.setCommissionRate(providerRate);
            commission.setProviderPayout(providerPayout);
            commission.setOrgAdminPayout(orgAdminPayout);
            if (commission.getGrossAmount() == null || commission.getGrossAmount() == 0.0) {
                commission.setGrossAmount(gross);
            }
        }

        AppointmentCommission saved = appointmentCommissionRepository.save(commission);

        // Synchronize settlement details on the appointment entity
        appointment.setSettlementStatus("SETTLED");
        appointment.setSettlementAmount(providerPayout);
        appointment.setOrgSettlementAmount(orgAdminPayout);
        appointment.setRemainingOrgAmount(remainingOrg);
        appointment.setNetRetainedAmount(netRetained);
        appointment.setGatewayFeeAmount(gatewayFee);
        appointmentRepository.save(appointment);

        return saved;
    }

    public List<AppointmentCommissionDTO> getCommissionsForTenant(Long tenantId) {
        String orgName = tenantRepository.findById(tenantId).map(Tenant::getOrganizationName).orElse("N/A");
        return appointmentCommissionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(c -> mapToDTO(c, orgName))
                .collect(Collectors.toList());
    }

    public List<AppointmentCommissionDTO> getAllRecentCommissions() {
        return appointmentCommissionRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(50)
                .map(c -> {
                    String orgName = tenantRepository.findById(c.getTenantId()).map(Tenant::getOrganizationName).orElse("N/A");
                    return mapToDTO(c, orgName);
                })
                .collect(Collectors.toList());
    }

    /**
     * Adjusts financial settlement and commission balances when an appointment is classified as NO_SHOW or CANCELLED/REFUNDED.
    /**
     * Computes applicable payment gateway processing fees on net retained volume.
     * If net retained is 0 or payment is cash/manual, gateway fee is 0.0.
     */
    public double calculateGatewayFee(String paymentMethod, double netRetainedAmount) {
        if (netRetainedAmount <= 0.0 || paymentMethod == null) {
            return 0.0;
        }
        if ("STRIPE".equalsIgnoreCase(paymentMethod)) {
            return Math.round((netRetainedAmount * 0.029) * 100.0) / 100.0;
        } else if ("ESEWA".equalsIgnoreCase(paymentMethod)) {
            return Math.round((netRetainedAmount * 0.020) * 100.0) / 100.0;
        }
        return 0.0;
    }

    /**
     * Adjusts financial settlement and commission balances when an appointment is classified as NO_SHOW or CANCELLED/REFUNDED.
     * Enforces the 3-tier settlement formula:
     * 1. Gross Booking Amount − Refund Amount = Net Retained Amount
     * 2. Net Retained Amount × Platform Commission % = Platform Commission
     * 3. Net Retained Amount − Platform Commission − Applicable Gateway Fees = Provider Settlement
     */
    @Transactional
    public AppointmentCommission adjustSettlementForNoShowOrRefund(Long appointmentId, double refundAmount, String finalStatus) {
        if (appointmentId == null) return null;

        AppointmentCommission commission = appointmentCommissionRepository
                .findByAppointmentId(appointmentId)
                .orElse(null);

        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);

        if (commission == null && appointment != null) {
            commission = recordAppointmentCommission(appointment, finalStatus);
        }

        if (commission != null) {
            double originalGross = commission.getGrossAmount() != null ? commission.getGrossAmount() : 0.0;
            double boundedRefund = Math.min(originalGross, Math.max(0.0, refundAmount));

            // 1. Gross Booking Amount − Refund Amount = Net Retained Amount
            double netRetained = Math.max(0.0, Math.round((originalGross - boundedRefund) * 100.0) / 100.0);

            // 2. Platform Commission (Platform SaaS fee on Net Retained Amount)
            double platformRate = commission.getPlatformCommissionRate() != null 
                    ? commission.getPlatformCommissionRate() 
                    : getCurrentCommissionRate();
            double platformCommission = Math.round((netRetained * (platformRate / 100.0)) * 100.0) / 100.0;

            // 3. Applicable Gateway Fees (on processed/retained volume)
            String paymentMethod = commission.getPaymentMethod() != null ? commission.getPaymentMethod() : (appointment != null ? appointment.getPaymentMethod() : "ESEWA");
            double gatewayFee = calculateGatewayFee(paymentMethod, netRetained);

            // 4. Gross Remaining Organization Amount (Net Retained - Platform Fee - Gateway Fee)
            double remainingOrg = Math.max(0.0, Math.round((netRetained - platformCommission - gatewayFee) * 100.0) / 100.0);

            // 5. Net Service Provider (Admin configured Provider Rate % on Gross Remaining Org Amount)
            double providerRate = commission.getCommissionRate() != null 
                    ? commission.getCommissionRate() 
                    : resolveCommissionRate(appointment != null ? appointment.getProviderId() : null, commission.getTenantId());
            double providerSettlement = Math.round((remainingOrg * (providerRate / 100.0)) * 100.0) / 100.0;

            // 6. Net Organization Admin (Remaining Org Amount - Net Service Provider)
            double orgAdminSettlement = Math.max(0.0, Math.round((remainingOrg - providerSettlement) * 100.0) / 100.0);

            commission.setRefundDeductionAmount(boundedRefund);
            commission.setNetRetainedAmount(netRetained);
            commission.setPlatformCommissionRate(platformRate);
            commission.setCommissionAmount(platformCommission);
            commission.setGatewayFeeAmount(gatewayFee);
            commission.setRemainingOrgAmount(remainingOrg);
            commission.setCommissionRate(providerRate);
            commission.setProviderPayout(providerSettlement);
            commission.setOrgAdminPayout(orgAdminSettlement);

            if (boundedRefund > 0 && netRetained > 0) {
                commission.setPaymentStatus("PARTIALLY_REFUNDED");
                commission.setSettlementStatus("ADJUSTED_REFUND");
            } else if (boundedRefund > 0 && netRetained == 0) {
                commission.setPaymentStatus("REFUNDED");
                commission.setSettlementStatus("FORFEITED");
            } else {
                // 0% refund (e.g. late cancellation or standard No-Show): Full settlement retained
                commission.setPaymentStatus("NO_SHOW".equalsIgnoreCase(finalStatus) ? "NO_SHOW_SETTLED" : "SETTLED");
                commission.setSettlementStatus("SETTLED");
            }

            appointmentCommissionRepository.save(commission);

            // Synchronize settlement details on the appointment entity
            if (appointment != null) {
                appointment.setSettlementStatus(commission.getSettlementStatus());
                appointment.setSettlementAmount(commission.getProviderPayout());
                appointment.setOrgSettlementAmount(commission.getOrgAdminPayout());
                appointment.setRemainingOrgAmount(remainingOrg);
                appointment.setNetRetainedAmount(netRetained);
                appointment.setGatewayFeeAmount(gatewayFee);
                appointmentRepository.save(appointment);
            }

            log.info("Settlement adjusted for appointmentId {}: originalGross={}, refund={}, netRetained={}, platformComm={}, gatewayFee={}, remainingOrg={}, providerPayout={}, orgAdminPayout={}, status={}",
                    appointmentId, originalGross, boundedRefund, netRetained, platformCommission, gatewayFee, remainingOrg, providerSettlement, orgAdminSettlement, commission.getPaymentStatus());
        }

        return commission;
    }

    private AppointmentCommissionDTO mapToDTO(AppointmentCommission c, String orgName) {
        return AppointmentCommissionDTO.builder()
                .id(c.getId())
                .appointmentId(c.getAppointmentId())
                .tenantId(c.getTenantId())
                .organizationName(orgName)
                .transactionId(c.getTransactionId())
                .grossAmount(c.getGrossAmount())
                .currency(c.getCurrency())
                .baseAmountNpr(c.getBaseAmountNpr())
                .exchangeRate(c.getExchangeRate())
                .commissionRate(c.getCommissionRate())
                .commissionAmount(c.getCommissionAmount())
                .providerPayout(c.getProviderPayout())
                .orgAdminPayout(c.getOrgAdminPayout())
                .remainingOrgAmount(c.getRemainingOrgAmount())
                .platformCommissionRate(c.getPlatformCommissionRate())
                .paymentStatus(c.getPaymentStatus())
                .settlementStatus(c.getSettlementStatus())
                .refundDeductionAmount(c.getRefundDeductionAmount())
                .netRetainedAmount(c.getNetRetainedAmount())
                .gatewayFeeAmount(c.getGatewayFeeAmount())
                .paymentMethod(c.getPaymentMethod())
                .paymentDate(c.getPaymentDate())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
