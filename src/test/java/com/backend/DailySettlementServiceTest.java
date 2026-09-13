package com.backend;

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
import com.backend.service.AuditLogService;
import com.backend.service.CommissionService;
import com.backend.service.DailySettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DailySettlementServiceTest {

    @Mock
    private DailySettlementRepository dailySettlementRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProviderProfileRepository providerProfileRepository;

    @Mock
    private CommissionService commissionService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private DailySettlementService dailySettlementService;

    private Tenant tenant;
    private User adminUser;
    private User providerUser;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(10L);
        tenant.setOrganizationName("Aura Wellness & Spa");
        tenant.setOrganizationType("BEAUTY_WELLNESS");

        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@auraspa.com");
        adminUser.setFullName("Spa Director");
        adminUser.setTenant(tenant);

        providerUser = new User();
        providerUser.setId(25L);
        providerUser.setEmail("stylist@auraspa.com");
        providerUser.setFullName("Elena Rostova");
        providerUser.setTenant(tenant);

        testDate = LocalDate.of(2026, 9, 11);
    }

    @Test
    void testDailyAggregationAndFourTierCalculation() {
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant));
        when(commissionService.getCurrentCommissionRate()).thenReturn(10.0); // 10% platform
        when(commissionService.resolveCommissionRate(25L, 10L)).thenReturn(10.0); // 10% provider
        when(commissionService.calculateGatewayFee("ESEWA", 500.0)).thenReturn(10.0); // 2% gateway

        // 1 appointment: Gross 1000, 50% refund = 500, net retained = 500
        Appointment appt = new Appointment();
        appt.setId(501L);
        appt.setTenantId(10L);
        appt.setProviderId(25L);
        appt.setAppointmentDate(testDate);
        appt.setAppointmentTime(LocalTime.of(10, 0));
        appt.setPrice(1000.0);
        appt.setRefundAmount(500.0);
        appt.setPaymentMethod("ESEWA");
        appt.setPaymentStatus("PARTIALLY_REFUNDED");
        appt.setAppointmentStatus("COMPLETED");

        when(appointmentRepository.findByTenantId(10L)).thenReturn(Collections.singletonList(appt));
        when(dailySettlementRepository.findByTenantIdAndSettlementDate(10L, testDate)).thenReturn(Optional.empty());

        DailySettlementDTOs.Detail detail = dailySettlementService.getDailySettlementDetail(10L, testDate);

        assertNotNull(detail);
        DailySettlementDTOs.Summary summary = detail.getSummary();
        assertNotNull(summary);
        assertEquals(10L, summary.getTenantId());
        assertEquals(testDate, summary.getSettlementDate());
        assertEquals(1, summary.getTotalAppointments());

        // 4-Tier Breakdown
        assertEquals(1000.0, summary.getGrossRevenue(), 0.001);
        assertEquals(500.0, summary.getTotalRefunds(), 0.001);
        assertEquals(500.0, summary.getNetRetained(), 0.001);
        assertEquals(50.0, summary.getPlatformCommission(), 0.001); // 10% of 500
        assertEquals(10.0, summary.getGatewayFees(), 0.001); // 2% of 500
        assertEquals(440.0, summary.getRemainingOrgAmount(), 0.001); // 500 - 50 - 10
        assertEquals(44.0, summary.getProviderPayoutsTotal(), 0.001); // 10% of 440
        assertEquals(396.0, summary.getOrgAdminPayout(), 0.001); // 440 - 44

        assertEquals("IN_ESCROW", summary.getSettlementStatus());
        assertFalse(summary.getIsLocked());
    }

    @Test
    void testFinalizeDailySettlementAndLockAudit() {
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmail("admin@auraspa.com")).thenReturn(Optional.of(adminUser));
        when(commissionService.getCurrentCommissionRate()).thenReturn(10.0);
        when(commissionService.resolveCommissionRate(25L, 10L)).thenReturn(10.0);
        when(commissionService.calculateGatewayFee("ESEWA", 1000.0)).thenReturn(20.0);

        Appointment appt = new Appointment();
        appt.setId(601L);
        appt.setTenantId(10L);
        appt.setProviderId(25L);
        appt.setAppointmentDate(testDate);
        appt.setPrice(1000.0);
        appt.setPaymentMethod("ESEWA");
        appt.setPaymentStatus("SUCCESS");
        appt.setAppointmentStatus("COMPLETED");

        when(appointmentRepository.findByTenantId(10L)).thenReturn(Collections.singletonList(appt));
        when(dailySettlementRepository.findByTenantIdAndSettlementDate(10L, testDate)).thenReturn(Optional.empty());

        when(dailySettlementRepository.save(any(DailySettlement.class))).thenAnswer(invocation -> {
            DailySettlement ds = invocation.getArgument(0);
            ds.setId(99L);
            return ds;
        });

        // Finalize settlement
        DailySettlementDTOs.Summary result = dailySettlementService.finalizeDailySettlement(10L, testDate, "admin@auraspa.com");

        assertNotNull(result);
        assertEquals("SETTLED", result.getSettlementStatus());
        assertTrue(result.getIsLocked());
        assertEquals(88.0, result.getProviderPayoutsTotal(), 0.001);
        assertEquals(792.0, result.getOrgAdminPayout(), 0.001);

        // Verify appointment was locked to this batch
        assertEquals(99L, appt.getDailySettlementId());
        assertEquals(testDate, appt.getSettlementBatchDate());
        assertEquals("SETTLED", appt.getSettlementStatus());
        assertEquals(88.0, appt.getSettlementAmount(), 0.001);
        assertEquals(792.0, appt.getOrgSettlementAmount(), 0.001);
        verify(appointmentRepository, times(1)).save(appt);
    }

    @Test
    void testRejectedAppointmentIsExcludedFromSettlement() {
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant));

        // Rejected appointment: Provider rejected, 100% refunded
        Appointment rejectedAppt = new Appointment();
        rejectedAppt.setId(701L);
        rejectedAppt.setTenantId(10L);
        rejectedAppt.setProviderId(25L);
        rejectedAppt.setAppointmentDate(testDate);
        rejectedAppt.setPrice(1500.0);
        rejectedAppt.setRefundAmount(1500.0);
        rejectedAppt.setPaymentMethod("ESEWA");
        rejectedAppt.setPaymentStatus("REFUNDED");
        rejectedAppt.setAppointmentStatus("REJECTED");
        rejectedAppt.setSettlementStatus("EXCLUDED");

        when(appointmentRepository.findByTenantId(10L)).thenReturn(Collections.singletonList(rejectedAppt));
        when(dailySettlementRepository.findByTenantIdAndSettlementDate(10L, testDate)).thenReturn(Optional.empty());

        DailySettlementDTOs.Detail detail = dailySettlementService.getDailySettlementDetail(10L, testDate);

        assertNotNull(detail);
        DailySettlementDTOs.Summary summary = detail.getSummary();
        assertNotNull(summary);
        // Excluded: 0 appointments, 0 revenue, 0 provider cut, 0 org net, 0 fees
        assertEquals(0, summary.getTotalAppointments());
        assertEquals(0.0, summary.getGrossRevenue(), 0.001);
        assertEquals(0.0, summary.getProviderPayoutsTotal(), 0.001);
        assertEquals(0.0, summary.getOrgAdminPayout(), 0.001);
        assertEquals(0.0, summary.getPlatformCommission(), 0.001);
        assertEquals(0.0, summary.getGatewayFees(), 0.001);
        assertFalse(detail.getIsEligibleForFinalization());
    }

    @Test
    void testLockedDailySettlementCannotBeReFinalized() {
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmail("admin@auraspa.com")).thenReturn(Optional.of(adminUser));

        DailySettlement lockedSettlement = DailySettlement.builder()
                .id(99L)
                .tenantId(10L)
                .settlementDate(testDate)
                .settlementStatus("SETTLED")
                .isLocked(true)
                .build();

        when(dailySettlementRepository.findByTenantIdAndSettlementDate(10L, testDate)).thenReturn(Optional.of(lockedSettlement));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            dailySettlementService.finalizeDailySettlement(10L, testDate, "admin@auraspa.com");
        });

        assertTrue(ex.getMessage().contains("already been finalized and locked for audit"));
    }

    @Test
    void testGetProviderDailySettlementDetailWhenSettled() {
        when(userRepository.findById(20L)).thenReturn(Optional.of(providerUser));
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant));
        when(commissionService.resolveCommissionRate(20L, 10L)).thenReturn(10.0);

        DailySettlement lockedSettlement = DailySettlement.builder()
                .id(99L)
                .tenantId(10L)
                .settlementDate(testDate)
                .settlementStatus("SETTLED")
                .isLocked(true)
                .platformCommissionRate(10.0)
                .settledAt(java.time.LocalDateTime.now())
                .build();

        when(dailySettlementRepository.findByTenantIdAndSettlementDate(10L, testDate)).thenReturn(Optional.of(lockedSettlement));

        Appointment appt = new Appointment();
        appt.setId(101L);
        appt.setTenantId(10L);
        appt.setProviderId(20L);
        appt.setPrice(500.0);
        appt.setAppointmentDate(testDate);
        appt.setAppointmentStatus("COMPLETED");
        appt.setPaymentStatus("SETTLED");
        appt.setDailySettlementId(99L);
        appt.setSettlementBatchDate(testDate);

        when(appointmentRepository.findByTenantId(10L)).thenReturn(Collections.singletonList(appt));

        DailySettlementDTOs.ProviderDailyView view = dailySettlementService.getProviderDailySettlementDetail(20L, 10L, testDate);

        assertNotNull(view);
        assertEquals("SETTLED", view.getStatus());
        assertTrue(view.getIsLocked());
        assertEquals(testDate, view.getDate());
        assertEquals(1, view.getAppointmentsCount());
        assertEquals(500.0, view.getAttributedGross(), 0.001);
        assertEquals(10.0, view.getCommissionRate(), 0.001);
        assertTrue(view.getNetProviderPayout() > 0);
        assertNotNull(view.getAppointments());
        assertEquals(1, view.getAppointments().size());
        assertEquals(101L, view.getAppointments().get(0).getAppointmentId());
        assertEquals("SETTLED", view.getAppointments().get(0).getSettlementStatus());
    }
}
