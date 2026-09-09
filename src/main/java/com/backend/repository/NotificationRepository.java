package com.backend.repository;

import com.backend.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findAllByOrderByCreatedAtDesc();
    
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    // --- Role Isolated Notification Queries ---

    // 1. SuperAdmin (Platform level, strictly tenantId IS NULL)
    @Query("SELECT n FROM Notification n WHERE (n.userId = :userId OR (n.targetRole = 'SUPER_ADMIN' AND n.tenantId IS NULL)) ORDER BY n.createdAt DESC")
    List<Notification> findSuperAdminNotifications(@Param("userId") Long userId);

    @Query("SELECT n FROM Notification n WHERE (n.userId = :userId OR (n.targetRole = 'SUPER_ADMIN' AND n.tenantId IS NULL)) AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(n.message) LIKE LOWER(CONCAT('%', :q, '%'))) ORDER BY n.createdAt DESC")
    List<Notification> searchSuperAdminNotifications(@Param("userId") Long userId, @Param("q") String q);

    @Query("SELECT COUNT(n) FROM Notification n WHERE (n.userId = :userId OR (n.targetRole = 'SUPER_ADMIN' AND n.tenantId IS NULL)) AND n.isRead = false")
    long countSuperAdminUnread(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE (n.userId = :userId OR (n.targetRole = 'SUPER_ADMIN' AND n.tenantId IS NULL)) AND n.isRead = false")
    void markSuperAdminAsRead(@Param("userId") Long userId);

    // 2. Tenant Admin (Organization level, strictly matching tenantId)
    @Query("SELECT n FROM Notification n WHERE n.tenantId = :tenantId AND (n.userId = :userId OR n.targetRole = 'ADMIN') ORDER BY n.createdAt DESC")
    List<Notification> findAdminNotifications(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    @Query("SELECT n FROM Notification n WHERE n.tenantId = :tenantId AND (n.userId = :userId OR n.targetRole = 'ADMIN') AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(n.message) LIKE LOWER(CONCAT('%', :q, '%'))) ORDER BY n.createdAt DESC")
    List<Notification> searchAdminNotifications(@Param("userId") Long userId, @Param("tenantId") Long tenantId, @Param("q") String q);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.tenantId = :tenantId AND (n.userId = :userId OR n.targetRole = 'ADMIN') AND n.isRead = false")
    long countAdminUnread(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.tenantId = :tenantId AND (n.userId = :userId OR n.targetRole = 'ADMIN') AND n.isRead = false")
    void markAdminAsRead(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    // 3. Service Provider (Doctor / Faculty / Staff within tenant)
    @Query("SELECT n FROM Notification n WHERE (n.userId = :userId OR (n.tenantId = :tenantId AND n.targetRole = 'SERVICE_PROVIDER' AND n.userId IS NULL)) ORDER BY n.createdAt DESC")
    List<Notification> findProviderNotifications(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    @Query("SELECT n FROM Notification n WHERE (n.userId = :userId OR (n.tenantId = :tenantId AND n.targetRole = 'SERVICE_PROVIDER' AND n.userId IS NULL)) AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(n.message) LIKE LOWER(CONCAT('%', :q, '%'))) ORDER BY n.createdAt DESC")
    List<Notification> searchProviderNotifications(@Param("userId") Long userId, @Param("tenantId") Long tenantId, @Param("q") String q);

    @Query("SELECT COUNT(n) FROM Notification n WHERE (n.userId = :userId OR (n.tenantId = :tenantId AND n.targetRole = 'SERVICE_PROVIDER' AND n.userId IS NULL)) AND n.isRead = false")
    long countProviderUnread(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE (n.userId = :userId OR (n.tenantId = :tenantId AND n.targetRole = 'SERVICE_PROVIDER' AND n.userId IS NULL)) AND n.isRead = false")
    void markProviderAsRead(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    // 4. User / Patient / Student (Personal notifications only)
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId ORDER BY n.createdAt DESC")
    List<Notification> findUserNotifications(@Param("userId") Long userId);

    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(n.message) LIKE LOWER(CONCAT('%', :q, '%'))) ORDER BY n.createdAt DESC")
    List<Notification> searchUserNotifications(@Param("userId") Long userId, @Param("q") String q);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.isRead = false")
    long countUserUnread(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    void markUserAsRead(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    void markAllAsReadByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.isRead = false")
    void markAllAsRead();
}
