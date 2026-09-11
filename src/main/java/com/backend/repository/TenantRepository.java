package com.backend.repository;

import com.backend.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    
    // Global metric: count active clinics
    @Query("SELECT COUNT(t) FROM Tenant t WHERE t.status = 'ACTIVE'")
    long countActiveClinics();

    boolean existsByRegistrationNumber(String registrationNumber);

    java.util.Optional<Tenant> findByRegistrationNumber(String registrationNumber);

    @Query("SELECT DISTINCT t.address FROM Tenant t WHERE t.status = 'ACTIVE' AND t.address IS NOT NULL")
    java.util.List<String> findDistinctAddress();

    java.util.List<Tenant> findByAddressAndStatus(String address, String status);
    
    java.util.List<Tenant> findByStatus(String status);
}
