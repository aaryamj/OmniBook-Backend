package com.backend;

import com.backend.model.AppointmentCommission;
import com.backend.model.SubscriptionOrder;
import com.backend.model.Tenant;
import com.backend.repository.AppointmentCommissionRepository;
import com.backend.repository.PlatformInvoiceRepository;
import com.backend.repository.SubscriptionOrderRepository;
import com.backend.repository.TenantRepository;
import com.backend.service.RevenueAnalyticsService;
import com.backend.service.SubscriptionPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RevenueAnalyticsServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private SubscriptionOrderRepository subscriptionOrderRepository;

    @Mock
    private PlatformInvoiceRepository platformInvoiceRepository;

    @Mock
    private AppointmentCommissionRepository appointmentCommissionRepository;

    @Mock
    private SubscriptionPlanService subscriptionPlanService;

    @InjectMocks
    private RevenueAnalyticsService revenueAnalyticsService;

    private Tenant monthlyTenant;
    private Tenant annualTenant;
    private Tenant unbilledTenant;

    @BeforeEach
    void setUp() {
        monthlyTenant = new Tenant();
        monthlyTenant.setId(1L);
        monthlyTenant.setOrganizationName("Dental Care Clinic");
        monthlyTenant.setRegistrationNumber("PAN-001");
        monthlyTenant.setStatus("ACTIVE");
        monthlyTenant.setSubscriptionTier("STARTER");
        monthlyTenant.setBillingCycle("MONTHLY");
        monthlyTenant.setCreatedAt(LocalDateTime.now().minusMonths(6));

        annualTenant = new Tenant();
        annualTenant.setId(2L);
        annualTenant.setOrganizationName("Apex Health Center");
        annualTenant.setRegistrationNumber("PAN-002");
        annualTenant.setStatus("ACTIVE");
        annualTenant.setSubscriptionTier("STARTER");
        annualTenant.setBillingCycle("ANNUAL");
        annualTenant.setCreatedAt(LocalDateTime.now().minusMonths(6));

        unbilledTenant = new Tenant();
        unbilledTenant.setId(3L);
        unbilledTenant.setOrganizationName("Community Wellness");
        unbilledTenant.setRegistrationNumber("PAN-003");
        unbilledTenant.setStatus("ACTIVE");
        unbilledTenant.setSubscriptionTier("PROFESSIONAL");
        unbilledTenant.setBillingCycle("MONTHLY");
        unbilledTenant.setCreatedAt(LocalDateTime.now().minusMonths(6));
    }

    @Test
    void testCalculateCurrentMRR_NormalizesAnnualAndUsesMonthlyPaid() {
        // Monthly order: Rs. 2,000 paid monthly
        SubscriptionOrder monthlyOrder = new SubscriptionOrder();
        monthlyOrder.setTenantId(1L);
        monthlyOrder.setAmount(2000.0);
        monthlyOrder.setBillingCycle("monthly");
        monthlyOrder.setPaymentStatus("PAID");

        // Annual order: Rs. 20,400 paid annually -> contributes Rs. 20,400 / 12.0 = 1,700 MRR
        SubscriptionOrder annualOrder = new SubscriptionOrder();
        annualOrder.setTenantId(2L);
        annualOrder.setAmount(20400.0);
        annualOrder.setBillingCycle("annually");
        annualOrder.setPaymentStatus("PAID");

        when(subscriptionOrderRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Arrays.asList(monthlyOrder, annualOrder));
        when(tenantRepository.findByStatus("ACTIVE")).thenReturn(Arrays.asList(monthlyTenant, annualTenant, unbilledTenant));
        
        // Fallback for unbilled tenant: PROFESSIONAL monthly catalog price = 5000.0
        when(subscriptionPlanService.getFallbackMonthlyPriceForTier("PROFESSIONAL")).thenReturn(5000.0);

        double totalMRR = revenueAnalyticsService.calculateTotalMRR();

        // Expected: 2000 (monthly) + 1700 (annual normalized) + 5000 (catalog fallback) = 8700.0
        assertEquals(8700.0, totalMRR, 0.01);
    }

    @Test
    void testSeparationOfMRRAndAppointmentCommission() {
        when(tenantRepository.findByStatus("ACTIVE")).thenReturn(Collections.singletonList(monthlyTenant));
        SubscriptionOrder monthlyOrder = new SubscriptionOrder();
        monthlyOrder.setTenantId(1L);
        monthlyOrder.setAmount(2000.0);
        monthlyOrder.setBillingCycle("monthly");
        monthlyOrder.setPaymentStatus("PAID");
        when(subscriptionOrderRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Collections.singletonList(monthlyOrder));

        double mrr = revenueAnalyticsService.calculateTotalMRR();
        assertEquals(2000.0, mrr, 0.01);

        // Appointment Commission is separate from MRR
        when(appointmentCommissionRepository.sumCommissionBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(450.0);
        double commission = revenueAnalyticsService.calculateAppointmentCommissionRevenueBetween(LocalDateTime.now().minusDays(30), LocalDateTime.now());
        assertEquals(450.0, commission, 0.01);

        // Commission must not bleed into subscription MRR
        assertEquals(2000.0, mrr, 0.01);
    }

    @Test
    void testCalculateTotalPlatformRevenue_SumsSubscriptionAndCommissionWithoutDoubleCounting() {
        SubscriptionOrder order = new SubscriptionOrder();
        order.setAmount(50000.0);
        order.setPaymentStatus("PAID");
        order.setCreatedAt(LocalDateTime.now().minusDays(5));

        when(subscriptionOrderRepository.findAll()).thenReturn(Collections.singletonList(order));
        when(appointmentCommissionRepository.sumCommissionBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(7500.0);

        double totalRevenue = revenueAnalyticsService.calculateTotalPlatformRevenueBetween(LocalDateTime.now().minusDays(30), LocalDateTime.now());

        // 50,000 subscription + 7,500 appointment commission = 57,500 total
        assertEquals(57500.0, totalRevenue, 0.01);
    }

    @Test
    void testBuildRevenueChartData_Generates1W1M1YDataPoints() {
        when(tenantRepository.findAll()).thenReturn(Collections.singletonList(monthlyTenant));
        when(subscriptionPlanService.getFallbackMonthlyPriceForTier("STARTER")).thenReturn(2000.0);

        Map<String, List<Double>> charts = revenueAnalyticsService.calculateHistoricalRevenueChartData();

        assertNotNull(charts);
        assertTrue(charts.containsKey("1W"));
        assertTrue(charts.containsKey("1M"));
        assertTrue(charts.containsKey("1Y"));
        assertEquals(7, charts.get("1W").size());
        assertEquals(4, charts.get("1M").size());
        assertEquals(12, charts.get("1Y").size());
    }
}
