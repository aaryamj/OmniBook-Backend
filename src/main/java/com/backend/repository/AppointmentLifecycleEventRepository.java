package com.backend.repository;

import com.backend.model.AppointmentLifecycleEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentLifecycleEventRepository extends JpaRepository<AppointmentLifecycleEvent, Long> {
    List<AppointmentLifecycleEvent> findByAppointmentIdOrderByCreatedAtAsc(Long appointmentId);
    List<AppointmentLifecycleEvent> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
