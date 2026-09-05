package com.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 1024)
    private String token; // We'll store a hash of the token or the token itself

    private String deviceOS;
    private String browser;
    private String ipAddress;
    private String location;

    private LocalDateTime loginAt;
    private LocalDateTime lastActiveAt;

    private boolean isActive;

    @PrePersist
    protected void onCreate() {
        loginAt = LocalDateTime.now();
        lastActiveAt = LocalDateTime.now();
        isActive = true;
    }
}
