package com.backend.repository;

import com.backend.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByTenantId(Long tenantId);
    List<Appointment> findByTenantIdOrderByAppointmentDateDesc(Long tenantId);
    List<Appointment> findByProviderIdOrderByAppointmentDateDesc(Long providerId);
    List<Appointment> findByPatientEmailOrderByAppointmentDateDesc(String email);
    List<Appointment> findByTransactionId(String transactionId);
    long countByCreatedAtAfter(java.time.LocalDateTime startDate);
    long countByTenantIdAndCreatedAtAfter(Long tenantId, java.time.LocalDateTime startDate);
    long countByTenantId(Long tenantId);

    @Query("SELECT a FROM Appointment a WHERE a.providerId = :providerId AND a.appointmentDate = :date AND UPPER(COALESCE(a.appointmentStatus, '')) NOT IN ('CANCELLED', 'REJECTED', 'EXPIRED')")
    List<Appointment> findActiveAppointmentsByProviderAndDate(@Param("providerId") Long providerId, @Param("date") LocalDate date);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.providerId = :providerId AND a.appointmentDate = :date AND a.appointmentTime = :time AND UPPER(COALESCE(a.appointmentStatus, '')) NOT IN ('CANCELLED', 'REJECTED', 'EXPIRED')")
    long countActiveAppointmentsForSlot(@Param("providerId") Long providerId, @Param("date") LocalDate date, @Param("time") LocalTime time);

    @Query("SELECT COUNT(a) > 0 FROM Appointment a WHERE " +
           "((:userId IS NOT NULL AND a.bookedByUserId = :userId) OR " +
           "(:email IS NOT NULL AND :email != '' AND LOWER(TRIM(a.patientEmail)) = LOWER(TRIM(:email)))) " +
           "AND a.appointmentDate = :date AND a.appointmentTime = :time " +
           "AND UPPER(COALESCE(a.appointmentStatus, '')) NOT IN ('CANCELLED', 'REJECTED', 'EXPIRED')")
    boolean existsActiveAppointmentForUserAtSlot(
            @Param("userId") Long userId,
            @Param("email") String email,
            @Param("date") LocalDate date,
            @Param("time") LocalTime time);

    @Query("SELECT a FROM Appointment a WHERE " +
           "((:userId IS NOT NULL AND a.bookedByUserId = :userId) OR " +
           "(:email IS NOT NULL AND :email != '' AND LOWER(TRIM(a.patientEmail)) = LOWER(TRIM(:email)))) " +
           "AND a.appointmentDate = :date " +
           "AND UPPER(COALESCE(a.appointmentStatus, '')) NOT IN ('CANCELLED', 'REJECTED', 'EXPIRED')")
    List<Appointment> findActiveUserAppointmentsOnDate(
            @Param("userId") Long userId,
            @Param("email") String email,
            @Param("date") LocalDate date);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE " +
           "((:userId IS NOT NULL AND a.bookedByUserId = :userId) OR " +
           "(:email IS NOT NULL AND :email != '' AND LOWER(TRIM(a.patientEmail)) = LOWER(TRIM(:email)))) " +
           "AND a.appointmentDate >= :today " +
           "AND UPPER(COALESCE(a.appointmentStatus, '')) NOT IN ('CANCELLED', 'REJECTED', 'EXPIRED', 'COMPLETED', 'NO_SHOW')")
    long countActiveUpcomingAppointmentsForUser(
            @Param("userId") Long userId,
            @Param("email") String email,
            @Param("today") LocalDate today);

    @Query("SELECT a FROM Appointment a WHERE " +
           "((:userId IS NOT NULL AND a.bookedByUserId = :userId) OR " +
           "(:email IS NOT NULL AND :email != '' AND LOWER(TRIM(a.patientEmail)) = LOWER(TRIM(:email)))) " +
           "AND a.appointmentDate >= :today " +
           "AND UPPER(COALESCE(a.appointmentStatus, '')) NOT IN ('CANCELLED', 'REJECTED', 'EXPIRED', 'COMPLETED', 'NO_SHOW') " +
           "ORDER BY a.appointmentDate ASC, a.appointmentTime ASC")
    List<Appointment> findActiveUpcomingAppointmentsForUser(
            @Param("userId") Long userId,
            @Param("email") String email,
            @Param("today") LocalDate today);
}

