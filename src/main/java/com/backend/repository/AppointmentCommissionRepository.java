package com.backend.repository;

import com.backend.model.AppointmentCommission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentCommissionRepository extends JpaRepository<AppointmentCommission, Long> {

    Optional<AppointmentCommission> findByAppointmentId(Long appointmentId);

    List<AppointmentCommission> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    List<AppointmentCommission> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0.0) FROM AppointmentCommission c WHERE c.paymentStatus IN ('SUCCESS', 'PAID', 'SETTLED')")
    Double sumTotalCommissionAllTime();

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0.0) FROM AppointmentCommission c WHERE c.paymentStatus IN ('SUCCESS', 'PAID', 'SETTLED') AND c.paymentDate >= :startDate AND c.paymentDate <= :endDate")
    Double sumCommissionBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(c.grossAmount), 0.0) FROM AppointmentCommission c WHERE c.paymentStatus IN ('SUCCESS', 'PAID', 'SETTLED') AND c.paymentDate >= :startDate AND c.paymentDate <= :endDate")
    Double sumGrossBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0.0) FROM AppointmentCommission c WHERE c.tenantId = :tenantId AND c.paymentStatus IN ('SUCCESS', 'PAID', 'SETTLED')")
    Double sumCommissionByTenant(@Param("tenantId") Long tenantId);
}
