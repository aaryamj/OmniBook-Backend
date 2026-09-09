package com.backend.service;

import com.backend.dto.ProviderAnalyticsDTO;
import com.backend.model.Appointment;
import com.backend.model.ProviderProfile;
import com.backend.model.ProviderService;
import com.backend.model.User;
import com.backend.repository.AppointmentRepository;
import com.backend.repository.ProviderProfileRepository;
import com.backend.repository.ProviderServiceRepository;
import com.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderAnalyticsService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ProviderServiceRepository providerServiceRepository;

    public ProviderAnalyticsDTO getProviderAnalytics(User provider, String range) {
        if (range == null || range.trim().isEmpty()) {
            range = "Last 30 Days";
        }
        String normalizedRange = range.trim();

        // 1. Fetch appointments for this provider / tenant
        List<Appointment> allAppointments;
        boolean isAdmin = "admin".equalsIgnoreCase(provider.getRole()) 
                || "role_admin".equalsIgnoreCase(provider.getRole()) 
                || "super_admin".equalsIgnoreCase(provider.getRole());

        if (isAdmin && provider.getTenant() != null) {
            allAppointments = appointmentRepository.findByTenantIdOrderByAppointmentDateDesc(provider.getTenant().getId());
        } else {
            allAppointments = appointmentRepository.findByProviderIdOrderByAppointmentDateDesc(provider.getId());
        }

        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate = today;

        LocalDate prevStartDate = null;
        LocalDate prevEndDate = null;

        if (normalizedRange.equalsIgnoreCase("Today")) {
            startDate = today;
            prevStartDate = today.minusDays(1);
            prevEndDate = today.minusDays(1);
        } else if (normalizedRange.equalsIgnoreCase("Last 7 Days")) {
            startDate = today.minusDays(6);
            prevStartDate = startDate.minusDays(7);
            prevEndDate = startDate.minusDays(1);
        } else if (normalizedRange.equalsIgnoreCase("Last 90 Days")) {
            startDate = today.minusDays(89);
            prevStartDate = startDate.minusDays(90);
            prevEndDate = startDate.minusDays(1);
        } else if (normalizedRange.equalsIgnoreCase("This Year")) {
            startDate = LocalDate.of(today.getYear(), 1, 1);
            prevStartDate = LocalDate.of(today.getYear() - 1, 1, 1);
            prevEndDate = LocalDate.of(today.getYear() - 1, 12, 31);
        } else if (normalizedRange.equalsIgnoreCase("All Time")) {
            startDate = null;
            endDate = null;
            prevStartDate = null;
            prevEndDate = null;
        } else {
            // Default: "Last 30 Days"
            startDate = today.minusDays(29);
            prevStartDate = startDate.minusDays(30);
            prevEndDate = startDate.minusDays(1);
        }

        // Filter current period appointments
        List<Appointment> currentPeriodAppts;
        if (normalizedRange.equalsIgnoreCase("All Time")) {
            // Include all records across past, present, and future
            currentPeriodAppts = allAppointments.stream()
                    .filter(a -> a.getAppointmentDate() != null)
                    .collect(Collectors.toList());
        } else {
            LocalDate finalStartDate = startDate;
            LocalDate finalEndDate = endDate;
            currentPeriodAppts = allAppointments.stream()
                    .filter(a -> a.getAppointmentDate() != null)
                    .filter(a -> !a.getAppointmentDate().isBefore(finalStartDate) && !a.getAppointmentDate().isAfter(finalEndDate))
                    .collect(Collectors.toList());
        }

        // Filter previous period appointments
        List<Appointment> prevPeriodAppts = new ArrayList<>();
        if (prevStartDate != null && prevEndDate != null) {
            LocalDate fPrevStart = prevStartDate;
            LocalDate fPrevEnd = prevEndDate;
            prevPeriodAppts = allAppointments.stream()
                    .filter(a -> a.getAppointmentDate() != null)
                    .filter(a -> !a.getAppointmentDate().isBefore(fPrevStart) && !a.getAppointmentDate().isAfter(fPrevEnd))
                    .collect(Collectors.toList());
        }

        // 2. Compute KPIs
        double currentRevenue = currentPeriodAppts.stream()
                .filter(a -> !"CANCELLED".equalsIgnoreCase(a.getAppointmentStatus()) && !"NO_SHOW".equalsIgnoreCase(a.getAppointmentStatus()))
                .mapToDouble(a -> a.getPrice() != null ? a.getPrice() : 0.0)
                .sum();

        double prevRevenue = prevPeriodAppts.stream()
                .filter(a -> !"CANCELLED".equalsIgnoreCase(a.getAppointmentStatus()) && !"NO_SHOW".equalsIgnoreCase(a.getAppointmentStatus()))
                .mapToDouble(a -> a.getPrice() != null ? a.getPrice() : 0.0)
                .sum();

        long currentTotalAppointments = currentPeriodAppts.size();
        long prevTotalAppointments = prevPeriodAppts.size();

        long currentCancelled = currentPeriodAppts.stream()
                .filter(a -> "CANCELLED".equalsIgnoreCase(a.getAppointmentStatus()) || "NO_SHOW".equalsIgnoreCase(a.getAppointmentStatus()))
                .count();
        double currentNoShowRate = currentTotalAppointments > 0 ? ((double) currentCancelled / currentTotalAppointments) * 100.0 : 0.0;

        long prevCancelled = prevPeriodAppts.stream()
                .filter(a -> "CANCELLED".equalsIgnoreCase(a.getAppointmentStatus()) || "NO_SHOW".equalsIgnoreCase(a.getAppointmentStatus()))
                .count();
        double prevNoShowRate = prevTotalAppointments > 0 ? ((double) prevCancelled / prevTotalAppointments) * 100.0 : 0.0;

        double revenueChangePct = 0.0;
        double appointmentsChangePct = 0.0;
        double noShowRateChangePct = 0.0;

        if (!normalizedRange.equalsIgnoreCase("All Time")) {
            if (prevRevenue > 0) {
                revenueChangePct = Math.round(((currentRevenue - prevRevenue) / prevRevenue) * 1000.0) / 10.0;
            } else if (currentRevenue > 0) {
                revenueChangePct = 100.0;
            }

            if (prevTotalAppointments > 0) {
                appointmentsChangePct = Math.round(((double)(currentTotalAppointments - prevTotalAppointments) / prevTotalAppointments) * 1000.0) / 10.0;
            } else if (currentTotalAppointments > 0) {
                appointmentsChangePct = 100.0;
            }

            noShowRateChangePct = Math.round((currentNoShowRate - prevNoShowRate) * 10.0) / 10.0;
        }

        // 3. Trends Aggregation
        List<ProviderAnalyticsDTO.TrendDataPoint> trends = computeTrends(currentPeriodAppts, normalizedRange, startDate, endDate);

        // 4. Top Services by Volume
        Map<String, List<Appointment>> serviceGroups = currentPeriodAppts.stream()
                .filter(a -> a.getServiceName() != null && !a.getServiceName().trim().isEmpty())
                .collect(Collectors.groupingBy(Appointment::getServiceName));

        List<ProviderAnalyticsDTO.ServiceVolumeDTO> topServices = serviceGroups.entrySet().stream()
                .map(entry -> {
                    String name = entry.getKey();
                    long volume = entry.getValue().size();
                    double rev = entry.getValue().stream()
                            .filter(a -> !"CANCELLED".equalsIgnoreCase(a.getAppointmentStatus()) && !"NO_SHOW".equalsIgnoreCase(a.getAppointmentStatus()))
                            .mapToDouble(a -> a.getPrice() != null ? a.getPrice() : 0.0)
                            .sum();
                    return ProviderAnalyticsDTO.ServiceVolumeDTO.builder()
                            .name(name)
                            .volume(volume)
                            .revenue(rev)
                            .build();
                })
                .sorted((a, b) -> Long.compare(b.getVolume(), a.getVolume()))
                .collect(Collectors.toList());

        // 5. Transactions mapping
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
        NumberFormat numFmt = NumberFormat.getInstance(Locale.US);

        List<ProviderAnalyticsDTO.AnalyticsTransactionDTO> transactions = currentPeriodAppts.stream()
                .sorted((a, b) -> {
                    int c = b.getAppointmentDate().compareTo(a.getAppointmentDate());
                    if (c != 0) return c;
                    if (a.getAppointmentTime() != null && b.getAppointmentTime() != null) {
                        return b.getAppointmentTime().compareTo(a.getAppointmentTime());
                    }
                    return Long.compare(b.getId(), a.getId());
                })
                .map(a -> {
                    String txnId = a.getTransactionId();
                    if (txnId == null || txnId.trim().isEmpty()) {
                        txnId = "TXN-" + String.format("%04d", a.getId());
                    } else if (!txnId.startsWith("#TXN-") && !txnId.startsWith("TXN-")) {
                        txnId = "TXN-" + (txnId.length() > 8 ? txnId.substring(0, 8).toUpperCase() : txnId.toUpperCase());
                    }
                    if (!txnId.startsWith("#")) {
                        txnId = "#" + txnId;
                    }

                    String status = "Pending";
                    if ("COMPLETED".equalsIgnoreCase(a.getAppointmentStatus()) || "SUCCESS".equalsIgnoreCase(a.getPaymentStatus())) {
                        status = "Confirmed";
                    } else if ("CANCELLED".equalsIgnoreCase(a.getAppointmentStatus())) {
                        status = "Cancelled";
                    } else if ("NO_SHOW".equalsIgnoreCase(a.getAppointmentStatus())) {
                        status = "No-Show";
                    }

                    double amount = a.getPrice() != null ? a.getPrice() : 0.0;
                    String formattedAmount = numFmt.format(Math.round(amount));

                    return ProviderAnalyticsDTO.AnalyticsTransactionDTO.builder()
                            .id(txnId)
                            .patient(a.getPatientName() != null ? a.getPatientName() : "Guest Client")
                            .service(a.getServiceName() != null ? a.getServiceName() : "General Consultation")
                            .amount(amount)
                            .amountFormatted(formattedAmount)
                            .status(status)
                            .paymentMethod(a.getPaymentMethod() != null ? a.getPaymentMethod() : "ESEWA")
                            .appointmentStatus(a.getAppointmentStatus())
                            .date(a.getAppointmentDate().format(dateFormatter))
                            .rawDate(a.getAppointmentDate().toString())
                            .build();
                })
                .collect(Collectors.toList());

        String formattedRevenue = "रू " + numFmt.format(Math.round(currentRevenue));

        return ProviderAnalyticsDTO.builder()
                .totalRevenue(currentRevenue)
                .formattedRevenue(formattedRevenue)
                .revenueChangePct(revenueChangePct)
                .totalAppointments(currentTotalAppointments)
                .appointmentsChangePct(appointmentsChangePct)
                .noShowRate(Math.round(currentNoShowRate * 10.0) / 10.0)
                .noShowRateChangePct(noShowRateChangePct)
                .trends(trends)
                .topServices(topServices)
                .transactions(transactions)
                .build();
    }

    private List<ProviderAnalyticsDTO.TrendDataPoint> computeTrends(List<Appointment> appts, String range, LocalDate start, LocalDate end) {
        List<ProviderAnalyticsDTO.TrendDataPoint> result = new ArrayList<>();

        if (range.equalsIgnoreCase("Today")) {
            // Hours
            String[] hours = {"09:00", "11:00", "13:00", "15:00", "17:00", "19:00"};
            for (int i = 0; i < hours.length; i++) {
                int h = 9 + (i * 2);
                double rev = appts.stream()
                        .filter(a -> a.getAppointmentTime() != null && a.getAppointmentTime().getHour() >= h && a.getAppointmentTime().getHour() < h + 2)
                        .filter(a -> !"CANCELLED".equalsIgnoreCase(a.getAppointmentStatus()) && !"NO_SHOW".equalsIgnoreCase(a.getAppointmentStatus()))
                        .mapToDouble(a -> a.getPrice() != null ? a.getPrice() : 0.0)
                        .sum();
                long count = appts.stream()
                        .filter(a -> a.getAppointmentTime() != null && a.getAppointmentTime().getHour() >= h && a.getAppointmentTime().getHour() < h + 2)
                        .count();
                result.add(new ProviderAnalyticsDTO.TrendDataPoint(hours[i], rev, count));
            }
        } else if (range.equalsIgnoreCase("Last 7 Days")) {
            DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("EEE");
            for (int i = 0; i < 7; i++) {
                LocalDate cur = start.plusDays(i);
                double rev = appts.stream()
                        .filter(a -> a.getAppointmentDate() != null && a.getAppointmentDate().isEqual(cur))
                        .filter(a -> !"CANCELLED".equalsIgnoreCase(a.getAppointmentStatus()) && !"NO_SHOW".equalsIgnoreCase(a.getAppointmentStatus()))
                        .mapToDouble(a -> a.getPrice() != null ? a.getPrice() : 0.0)
                        .sum();
                long count = appts.stream()
                        .filter(a -> a.getAppointmentDate() != null && a.getAppointmentDate().isEqual(cur))
                        .count();
                result.add(new ProviderAnalyticsDTO.TrendDataPoint(cur.format(dayFormatter), rev, count));
            }
        } else if (range.equalsIgnoreCase("Last 30 Days")) {
            // 5 intervals of ~6 days
            for (int i = 0; i < 5; i++) {
                LocalDate bStart = start.plusDays(i * 6);
                LocalDate bEnd = i == 4 ? end : start.plusDays((i * 6) + 5);
                String label = bStart.format(DateTimeFormatter.ofPattern("MMM d"));
                double rev = appts.stream()
                        .filter(a -> a.getAppointmentDate() != null && !a.getAppointmentDate().isBefore(bStart) && !a.getAppointmentDate().isAfter(bEnd))
                        .filter(a -> !"CANCELLED".equalsIgnoreCase(a.getAppointmentStatus()) && !"NO_SHOW".equalsIgnoreCase(a.getAppointmentStatus()))
                        .mapToDouble(a -> a.getPrice() != null ? a.getPrice() : 0.0)
                        .sum();
                long count = appts.stream()
                        .filter(a -> a.getAppointmentDate() != null && !a.getAppointmentDate().isBefore(bStart) && !a.getAppointmentDate().isAfter(bEnd))
                        .count();
                result.add(new ProviderAnalyticsDTO.TrendDataPoint(label, rev, count));
            }
        } else if (range.equalsIgnoreCase("All Time")) {
            LocalDate today = LocalDate.now();
            LocalDate minDate = appts.stream()
                    .map(Appointment::getAppointmentDate)
                    .filter(Objects::nonNull)
                    .min(LocalDate::compareTo)
                    .orElse(today.minusMonths(5));
            LocalDate maxDate = appts.stream()
                    .map(Appointment::getAppointmentDate)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(today);

            if (minDate.isAfter(today.minusMonths(2))) {
                minDate = today.minusMonths(2);
            }
            if (maxDate.isBefore(today)) {
                maxDate = today;
            }

            LocalDate curMonthDate = LocalDate.of(minDate.getYear(), minDate.getMonthValue(), 1);
            LocalDate lastMonthDate = LocalDate.of(maxDate.getYear(), maxDate.getMonthValue(), 1);

            boolean multiYear = minDate.getYear() != maxDate.getYear();
            DateTimeFormatter monthFmt = multiYear ? DateTimeFormatter.ofPattern("MMM yy") : DateTimeFormatter.ofPattern("MMM");

            while (!curMonthDate.isAfter(lastMonthDate)) {
                int m = curMonthDate.getMonthValue();
                int y = curMonthDate.getYear();
                String monthLabel = curMonthDate.format(monthFmt);

                double rev = appts.stream()
                        .filter(a -> a.getAppointmentDate() != null && a.getAppointmentDate().getMonthValue() == m && a.getAppointmentDate().getYear() == y)
                        .filter(a -> !"CANCELLED".equalsIgnoreCase(a.getAppointmentStatus()) && !"NO_SHOW".equalsIgnoreCase(a.getAppointmentStatus()))
                        .mapToDouble(a -> a.getPrice() != null ? a.getPrice() : 0.0)
                        .sum();
                long count = appts.stream()
                        .filter(a -> a.getAppointmentDate() != null && a.getAppointmentDate().getMonthValue() == m && a.getAppointmentDate().getYear() == y)
                        .count();
                result.add(new ProviderAnalyticsDTO.TrendDataPoint(monthLabel, rev, count));
                curMonthDate = curMonthDate.plusMonths(1);
            }
        } else {
            // Monthly intervals
            if (start != null && end != null) {
                int totalMonths = (end.getYear() - start.getYear()) * 12 + (end.getMonthValue() - start.getMonthValue()) + 1;
                totalMonths = Math.min(Math.max(totalMonths, 3), 12);

                for (int i = 0; i < totalMonths; i++) {
                    LocalDate curMonthDate = start.plusMonths(i);
                    if (curMonthDate.isAfter(end)) break;
                    String monthLabel = curMonthDate.format(DateTimeFormatter.ofPattern("MMM"));
                    int m = curMonthDate.getMonthValue();
                    int y = curMonthDate.getYear();

                    double rev = appts.stream()
                            .filter(a -> a.getAppointmentDate() != null && a.getAppointmentDate().getMonthValue() == m && a.getAppointmentDate().getYear() == y)
                            .filter(a -> !"CANCELLED".equalsIgnoreCase(a.getAppointmentStatus()) && !"NO_SHOW".equalsIgnoreCase(a.getAppointmentStatus()))
                            .mapToDouble(a -> a.getPrice() != null ? a.getPrice() : 0.0)
                            .sum();
                    long count = appts.stream()
                            .filter(a -> a.getAppointmentDate() != null && a.getAppointmentDate().getMonthValue() == m && a.getAppointmentDate().getYear() == y)
                            .count();
                    result.add(new ProviderAnalyticsDTO.TrendDataPoint(monthLabel, rev, count));
                }
            }
        }

        return result;
    }
}
