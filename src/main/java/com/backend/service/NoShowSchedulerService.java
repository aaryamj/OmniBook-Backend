package com.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoShowSchedulerService {

    private final AppointmentLifecycleService appointmentLifecycleService;

    /**
     * Periodically sweeps active appointments and classifies unattended appointments
     * past their organization's configured grace period as NO_SHOW.
     * Runs every 2 minutes with an initial 10-second delay.
     */
    @Scheduled(fixedDelay = 120000, initialDelay = 10000)
    public void scheduleNoShowClassification() {
        try {
            int classified = appointmentLifecycleService.processAutomatedNoShows();
            if (classified > 0) {
                log.info("NoShowSchedulerService: Successfully processed {} unattended appointments as NO_SHOW.", classified);
            }
        } catch (Exception e) {
            log.error("NoShowSchedulerService error during automated sweep: {}", e.getMessage());
        }
    }
}
