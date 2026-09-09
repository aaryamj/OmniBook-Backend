package com.backend.controller;

import com.backend.model.Notification;
import com.backend.model.User;
import com.backend.repository.UserRepository;
import com.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<?> getUserNotifications(@RequestParam(value = "search", required = false) String search) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<Notification> notifications = notificationService.getNotificationsForUser(user, search);
            long unreadCount = notificationService.getUnreadCountForUser(user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("unreadCount", unreadCount);
            response.put("notifications", notifications);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error fetching notifications: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/read-all")
    public ResponseEntity<?> markAllAsReadPut() {
        return handleMarkAllRead();
    }

    @PostMapping("/mark-read")
    public ResponseEntity<?> markAllAsReadPost() {
        return handleMarkAllRead();
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markSingleAsRead(@PathVariable("id") Long id) {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            boolean updated = notificationService.markSingleAsRead(id, user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", updated);
            response.put("message", updated ? "Notification marked as read" : "Notification not found or unauthorized");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error updating notification: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    private ResponseEntity<?> handleMarkAllRead() {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            notificationService.markAllReadForUser(user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "All notifications marked as read");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error updating notifications: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
