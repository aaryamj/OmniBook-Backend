package com.backend.service;

import com.backend.model.Tenant;
import com.backend.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {
    
    private final TenantRepository tenantRepository;
    private final NotificationService notificationService;
    
    @Transactional
    public Tenant updateTenantProfile(
            Long tenantId, 
            String primaryAccentColor,
            String phoneContact,
            String timezone,
            String openingTimeStr,
            String closingTimeStr,
            Integer slotDuration,
            String logoUrl,
            Boolean twoFactorEnabled,
            Integer sessionTimeout
    ) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
                
        if (primaryAccentColor != null && !primaryAccentColor.isEmpty()) {
            tenant.setPrimaryAccentColor(primaryAccentColor);
        }
        if (phoneContact != null && !phoneContact.isEmpty()) {
            tenant.setPhoneContact(phoneContact);
        }
        if (timezone != null && !timezone.isEmpty()) {
            tenant.setTimezone(timezone);
        }
        if (openingTimeStr != null && !openingTimeStr.isEmpty()) {
            tenant.setOpeningTime(LocalTime.parse(openingTimeStr));
        }
        if (closingTimeStr != null && !closingTimeStr.isEmpty()) {
            tenant.setClosingTime(LocalTime.parse(closingTimeStr));
        }
        if (slotDuration != null) {
            tenant.setSlotDuration(slotDuration);
        }
        if (logoUrl != null && !logoUrl.isEmpty()) {
            tenant.setLogoUrl(logoUrl);
        }
        if (twoFactorEnabled != null) {
            tenant.setTwoFactorEnabled(twoFactorEnabled);
        }
        if (sessionTimeout != null) {
            tenant.setSessionTimeout(sessionTimeout);
        }
        
        return tenantRepository.save(tenant);
    }

    @Transactional
    public Tenant updateTenantFinancials(
            Long tenantId,
            String legalBusinessName,
            String businessEntityType,
            String ibanAccountNumber,
            String medicalLicenseUrl
    ) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        if (legalBusinessName != null && !legalBusinessName.isEmpty()) {
            tenant.setLegalBusinessName(legalBusinessName);
        }
        if (businessEntityType != null && !businessEntityType.isEmpty()) {
            tenant.setBusinessEntityType(businessEntityType);
        }
        if (ibanAccountNumber != null && !ibanAccountNumber.isEmpty()) {
            tenant.setIbanAccountNumber(ibanAccountNumber);
        }
        if (medicalLicenseUrl != null && !medicalLicenseUrl.isEmpty()) {
            tenant.setMedicalLicenseUrl(medicalLicenseUrl);
        }
        
        // Under Option 1: Manual Verification, we do NOT auto-approve KYC.
        // Instead, we mark the tenant's overall status as PENDING_VERIFICATION.
        tenant.setKycVerified(false);
        tenant.setStatus("PENDING_VERIFICATION");

        notificationService.createNotification(
                "Action Required: Verify Clinic",
                tenant.getOrganizationName() + " has completed onboarding and is awaiting verification.",
                "TENANT_PENDING"
        );

        return tenantRepository.save(tenant);
    }
}
