package com.backend.service;

import com.backend.model.Reminder;
import com.backend.model.User;
import com.backend.repository.ReminderRepository;
import com.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledTaskService {

    private final ReminderRepository reminderRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;

    // Runs every day at 8:00 AM server time
    @Scheduled(cron = "0 0 8 * * ?")
    public void processDailyReminders() {
        LocalDate today = LocalDate.now();
        log.info("Running daily scheduled task for reminders due on {}", today);

        List<Reminder> dueReminders = reminderRepository.findByDueDateAndIsSentFalse(today);
        
        for (Reminder reminder : dueReminders) {
            try {
                if (reminder.getPatientEmail() != null) {
                    Optional<User> providerOpt = userRepository.findById(reminder.getProviderId());
                    String providerName = providerOpt.map(User::getFullName).orElse("your provider");
                    
                    emailService.sendRecallReminder(reminder.getPatientEmail(), providerName, reminder.getMessage());
                }
                
                reminder.setIsSent(true);
                reminderRepository.save(reminder);
                log.info("Sent reminder id {} to {}", reminder.getId(), reminder.getPatientEmail());
            } catch (Exception e) {
                log.error("Failed to process reminder id {}: {}", reminder.getId(), e.getMessage());
            }
        }
    }
}
