package com.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSlotDTO {
    private String id;
    private String title;
    private String date; // E.g., "Nov 23, 2024"
    private String time; // E.g., "10:00 AM"
    private String price; // E.g., "रू 1,500"
    private String provider; // E.g., "Dr. Sarah - Consultation"
    private Boolean topMatch;
    private String providerId;
    
    private Boolean isBreak;
    private Boolean isPast;
    private Boolean isBooked; // true if already booked by an existing appointment ("Sold Out")
    private Boolean isCompleted; // true if already completed by the provider
    private String slotStatus; // e.g. "COMPLETED", "SCHEDULED", "CHECKED_IN", "APPROVED"
    private String slotTime24; // "HH:mm" 24-hour format e.g. "10:30"
    private String providerImageUrl;

    private Integer maxCapacity;
    private Integer currentBookings;
    private Integer availableSeats;
    private Boolean isFull;
    private Boolean alreadyBookedByUser;
}
