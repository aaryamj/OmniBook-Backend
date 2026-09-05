package com.backend.repository;

import com.backend.model.TenantSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenantScheduleRepository extends JpaRepository<TenantSchedule, Long> {
    List<TenantSchedule> findByTenantId(Long tenantId);
}
