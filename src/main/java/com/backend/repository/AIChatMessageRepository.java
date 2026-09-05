package com.backend.repository;

import com.backend.model.AIChatMessage;
import com.backend.model.AIConversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AIChatMessageRepository extends JpaRepository<AIChatMessage, Long> {

    List<AIChatMessage> findByConversationOrderByCreatedAtAsc(AIConversation conversation);

    // Get the most recent N messages ordered by createdAt descending (used to fetch the last 20 turns)
    @Query("SELECT m FROM AIChatMessage m WHERE m.conversation = :conv ORDER BY m.createdAt DESC")
    List<AIChatMessage> findRecentMessagesByConversation(@Param("conv") AIConversation conversation, Pageable pageable);

    // Find messages that haven't been summarized yet and are older than a specific message ID
    @Query("SELECT m FROM AIChatMessage m WHERE m.conversation = :conv AND m.isSummarized = false AND m.id < :maxId ORDER BY m.createdAt ASC")
    List<AIChatMessage> findUnsummarizedMessagesBefore(@Param("conv") AIConversation conversation, @Param("maxId") Long maxId);

    long countByConversation(AIConversation conversation);
}
