package com.spry.library.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationLogRetentionJob {

    private final NotificationLogRepository notificationLogRepository;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeOldEntries() {
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        int deleted = notificationLogRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Purged {} notification log entries older than 90 days", deleted);
        }
    }
}
