package com.backend.repository;

import com.backend.model.SystemAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemAnnouncementRepository extends JpaRepository<SystemAnnouncement, Long> {
    List<SystemAnnouncement> findAllByOrderByCreatedAtDesc();
}
