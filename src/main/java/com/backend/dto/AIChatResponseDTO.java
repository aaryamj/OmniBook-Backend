package com.backend.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIChatResponseDTO {
    private String conversationId;
    private String responseText;
    private String actionSlotId; // Can be null if the AI doesn't recommend a specific slot to book
    private String actionType; // SELECT_SLOT, CANCEL_APPOINTMENT, RESCHEDULE_APPOINTMENT, INFO_ONLY
    private Object actionPayload;// Additional metadata for action execution
    private String organizationType;
    private String matchReason;
    private List<AISlotDTO> recommendedSlots;
}
