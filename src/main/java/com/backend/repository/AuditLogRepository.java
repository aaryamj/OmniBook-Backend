package com.backend.repository;

import com.backend.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop20ByTenantIdOrderByTimestampDesc(Long tenantId);
    List<AuditLog> findTop50ByOrderByTimestampDesc();
    List<AuditLog> findTop50ByTimestampAfterOrderByTimestampDesc(java.time.LocalDateTime startDate);
    
    long count();
    long countByTimestampAfter(java.time.LocalDateTime startDate);
    
    long countByEventActionIn(List<String> eventActions);
    long countByEventActionInAndTimestampAfter(List<String> eventActions, java.time.LocalDateTime startDate);
    
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(a) FROM AuditLog a WHERE a.user.role = :role")
    long countByUserRole(@org.springframework.data.repository.query.Param("role") String role);
    
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(a) FROM AuditLog a WHERE a.user.role = :role AND a.timestamp >= :startDate")
    long countByUserRoleAndTimestampAfter(@org.springframework.data.repository.query.Param("role") String role, @org.springframework.data.repository.query.Param("startDate") java.time.LocalDateTime startDate);
    
    @org.springframework.data.jpa.repository.Query(
        value = "SELECT COUNT(*) as count, HOUR(timestamp) as hour " +
                "FROM audit_logs " +
                "WHERE timestamp >= NOW() - INTERVAL 24 HOUR " +
                "GROUP BY HOUR(timestamp) " +
                "ORDER BY MAX(timestamp) ASC", 
        nativeQuery = true)
    List<Object[]> getLogFrequencyLast24Hours();
}
