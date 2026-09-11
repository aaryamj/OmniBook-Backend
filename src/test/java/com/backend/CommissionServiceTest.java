package com.backend;

import com.backend.model.Appointment;
import com.backend.model.AppointmentCommission;
import com.backend.model.PlatformSetting;
import com.backend.repository.AppointmentCommissionRepository;
import com.backend.repository.PlatformSettingRepository;
import com.backend.service.CommissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommissionServiceTest {

    @Mock
    private AppointmentCommissionRepository appointmentCommissionRepository;

    @Mock
    private PlatformSettingRepository platformSettingRepository;

    @Mock
    private com.backend.repository.AppointmentRepository appointmentRepository;

    @Mock
    private com.backend.repository.TenantRepository tenantRepository;

    @Mock
    private com.backend.repository.ProviderProfileRepository providerProfileRepository;

    @InjectMocks
    private CommissionService commissionService;

    private PlatformSetting platformSetting;

    @BeforeEach
    void setUp() {
        platformSetting = new PlatformSetting();
        platformSetting.setId(1L);
        platformSetting.setAppointmentCommissionRate(10.0);
    }

    @Test
    void testCalculateAndRecordCommission_Default10Percent() {
        when(platformSettingRepository.findAll()).thenReturn(Collections.singletonList(platformSetting));
        when(appointmentCommissionRepository.findByAppointmentId(101L)).thenReturn(Optional.empty());

        Appointment appointment = new Appointment();
        appointment.setId(101L);
        appointment.setTenantId(5L);
        appointment.setPrice(1000.0);
        appointment.setPaymentMethod("ESEWA");
        appointment.setTransactionId("TXN-998877");

        // When a Rs. 1,000 appointment is completed
        commissionService.recordAppointmentCommission(appointment, "SUCCESS");

        ArgumentCaptor<AppointmentCommission> captor = ArgumentCaptor.forClass(AppointmentCommission.class);
        verify(appointmentCommissionRepository, times(1)).save(captor.capture());

        AppointmentCommission recorded = captor.getValue();
        assertNotNull(recorded);
        assertEquals(101L, recorded.getAppointmentId());
        assertEquals(5L, recorded.getTenantId());
        assertEquals(1000.0, recorded.getGrossAmount(), 0.001);
        assertEquals(1000.0, recorded.getNetRetainedAmount(), 0.001);
        assertEquals(10.0, recorded.getPlatformCommissionRate(), 0.001);
        // Rs. 1,000 * 10% = Rs. 100 platform commission
        assertEquals(100.0, recorded.getCommissionAmount(), 0.001);
        // 2% eSewa Gateway Fee = Rs. 20
        assertEquals(20.0, recorded.getGatewayFeeAmount(), 0.001);
        // Gross Remaining Org = 1000 - 100 - 20 = Rs. 880
        assertEquals(880.0, recorded.getRemainingOrgAmount(), 0.001);
        // Provider Share 10% of 880 = Rs. 88
        assertEquals(88.0, recorded.getProviderPayout(), 0.001);
        // Org Admin Share 880 - 88 = Rs. 792
        assertEquals(792.0, recorded.getOrgAdminPayout(), 0.001);
        assertEquals("ESEWA", recorded.getPaymentMethod());
        assertEquals("TXN-998877", recorded.getTransactionId());
        assertEquals("SUCCESS", recorded.getPaymentStatus());
    }

    @Test
    void testUserScenario_50PercentRefund_FourTierSettlement() {
        Appointment appointment = new Appointment();
        appointment.setId(301L);
        appointment.setTenantId(9L);
        appointment.setProviderId(23L);
        appointment.setPrice(1000.0);
        appointment.setPaymentMethod("ESEWA");
        appointment.setTransactionId("TXN-301");

        AppointmentCommission existingCommission = AppointmentCommission.builder()
                .id(50L)
                .appointmentId(301L)
                .tenantId(9L)
                .grossAmount(1000.0)
                .netRetainedAmount(1000.0)
                .platformCommissionRate(10.0)
                .commissionAmount(100.0)
                .gatewayFeeAmount(20.0)
                .remainingOrgAmount(880.0)
                .commissionRate(10.0)
                .providerPayout(88.0)
                .orgAdminPayout(792.0)
                .paymentMethod("ESEWA")
                .paymentStatus("SUCCESS")
                .settlementStatus("SETTLED")
                .build();

        when(appointmentCommissionRepository.findByAppointmentId(301L)).thenReturn(Optional.of(existingCommission));
        when(appointmentRepository.findById(301L)).thenReturn(Optional.of(appointment));

        // Adjust for 50% refund (NPR 500)
        AppointmentCommission adjusted = commissionService.adjustSettlementForNoShowOrRefund(301L, 500.0, "PARTIALLY_REFUNDED");

        assertNotNull(adjusted);
        // 1. Gross Booking = 1000, Refund = 500
        assertEquals(1000.0, adjusted.getGrossAmount(), 0.001);
        assertEquals(500.0, adjusted.getRefundDeductionAmount(), 0.001);
        // 2. Net Retained Amount = 500
        assertEquals(500.0, adjusted.getNetRetainedAmount(), 0.001);
        // 3. Platform Commission (10%) = 50
        assertEquals(50.0, adjusted.getCommissionAmount(), 0.001);
        // 4. Applicable Gateway Fees (2% eSewa on retained 500) = 10
        assertEquals(10.0, adjusted.getGatewayFeeAmount(), 0.001);
        // 5. Gross Remaining Organization Amount = 500 - 50 - 10 = 440
        assertEquals(440.0, adjusted.getRemainingOrgAmount(), 0.001);
        // 6. Net Service Provider (10% of 440) = 44
        assertEquals(44.0, adjusted.getProviderPayout(), 0.001);
        // 7. Net Organization Admin (440 - 44) = 396
        assertEquals(396.0, adjusted.getOrgAdminPayout(), 0.001);
        assertEquals("PARTIALLY_REFUNDED", adjusted.getPaymentStatus());
        assertEquals("ADJUSTED_REFUND", adjusted.getSettlementStatus());
    }

    @Test
    void testHistoricalCommissionPreservedWhenRateChanges() {
        when(platformSettingRepository.findAll()).thenReturn(Collections.singletonList(platformSetting));

        Appointment apt1 = new Appointment();
        apt1.setId(201L);
        apt1.setTenantId(5L);
        apt1.setPrice(1000.0);
        apt1.setPaymentMethod("ESEWA");
        apt1.setTransactionId("TXN-1");

        when(appointmentCommissionRepository.findByAppointmentId(201L)).thenReturn(Optional.empty());

        // Record at 10%
        commissionService.recordAppointmentCommission(apt1, "SUCCESS");

        // Now Super Admin updates rate to 15%
        commissionService.updateCommissionRate(15.0);
        assertEquals(15.0, platformSetting.getAppointmentCommissionRate(), 0.001);

        Appointment apt2 = new Appointment();
        apt2.setId(202L);
        apt2.setTenantId(5L);
        apt2.setPrice(1000.0);
        apt2.setPaymentMethod("ESEWA");
        apt2.setTransactionId("TXN-2");

        when(appointmentCommissionRepository.findByAppointmentId(202L)).thenReturn(Optional.empty());

        // Record another appointment under 15%
        commissionService.recordAppointmentCommission(apt2, "SUCCESS");

        ArgumentCaptor<AppointmentCommission> captor = ArgumentCaptor.forClass(AppointmentCommission.class);
        verify(appointmentCommissionRepository, times(2)).save(captor.capture());

        AppointmentCommission first = captor.getAllValues().get(0);
        AppointmentCommission second = captor.getAllValues().get(1);

        // Historical record retains original 10% rate and Rs. 100 commission
        assertEquals(10.0, first.getPlatformCommissionRate(), 0.001);
        assertEquals(100.0, first.getCommissionAmount(), 0.001);

        // New record reflects 15% rate and Rs. 150 commission
        assertEquals(15.0, second.getPlatformCommissionRate(), 0.001);
        assertEquals(150.0, second.getCommissionAmount(), 0.001);
    }
}
