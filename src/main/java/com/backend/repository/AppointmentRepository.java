package com.backend.repository;

import com.backend.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByTenantId(Long tenantId);
    List<Appointment> findByProviderIdOrderByAppointmentDateDesc(Long providerId);
    List<Appointment> findByPatientEmailOrderByAppointmentDateDesc(String email);
    List<Appointment> findByTransactionId(String transactionId);
    long countByCreatedAtAfter(java.time.LocalDateTime startDate);
    long countByTenantIdAndCreatedAtAfter(Long tenantId, java.time.LocalDateTime startDate);
    long countByTenantId(Long tenantId);
}
