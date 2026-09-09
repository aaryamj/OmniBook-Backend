package com.backend.repository;

import com.backend.model.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    Optional<SupportTicket> findByTicketNumber(String ticketNumber);

    List<SupportTicket> findAllByOrderByCreatedAtDesc();

    @Query("SELECT s FROM SupportTicket s WHERE s.createdAt >= :startDate ORDER BY s.createdAt DESC")
    List<SupportTicket> findByCreatedAtAfter(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT s FROM SupportTicket s WHERE s.issueType = :issueType ORDER BY s.createdAt DESC")
    List<SupportTicket> findByIssueType(@Param("issueType") String issueType);

    @Query("SELECT s FROM SupportTicket s WHERE s.createdAt >= :startDate AND s.issueType = :issueType ORDER BY s.createdAt DESC")
    List<SupportTicket> findByCreatedAtAfterAndIssueType(
            @Param("startDate") LocalDateTime startDate,
            @Param("issueType") String issueType
    );

    @Query("SELECT COUNT(s) FROM SupportTicket s WHERE s.status NOT IN ('Closed', 'Resolved')")
    long countOpenTickets();

    @Query("SELECT COUNT(s) FROM SupportTicket s WHERE s.createdAt >= :startDate AND s.status NOT IN ('Closed', 'Resolved')")
    long countOpenTicketsSince(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT COUNT(s) FROM SupportTicket s WHERE s.priority = 'Urgent' AND s.slaMinutesRemaining <= 0")
    long countSlaBreaches();

    @Query("SELECT COUNT(s) FROM SupportTicket s WHERE s.createdAt >= :startDate AND s.priority = 'Urgent' AND s.slaMinutesRemaining <= 0")
    long countSlaBreachesSince(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT AVG(s.resolutionHours) FROM SupportTicket s WHERE s.resolutionHours IS NOT NULL")
    Double getAverageResolutionHours();

    @Query("SELECT AVG(s.resolutionHours) FROM SupportTicket s WHERE s.createdAt >= :startDate AND s.resolutionHours IS NOT NULL")
    Double getAverageResolutionHoursSince(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT AVG(s.csatRating) FROM SupportTicket s WHERE s.csatRating IS NOT NULL")
    Double getAverageCsatScore();

    @Query("SELECT AVG(s.csatRating) FROM SupportTicket s WHERE s.createdAt >= :startDate AND s.csatRating IS NOT NULL")
    Double getAverageCsatScoreSince(@Param("startDate") LocalDateTime startDate);
}
