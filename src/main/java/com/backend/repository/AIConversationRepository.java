package com.backend.repository;

import com.backend.model.AIConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIConversationRepository extends JpaRepository<AIConversation, Long> {
    Optional<AIConversation> findByConversationId(String conversationId);
    List<AIConversation> findByUserEmailOrderByUpdatedAtDesc(String userEmail);
    List<AIConversation> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
