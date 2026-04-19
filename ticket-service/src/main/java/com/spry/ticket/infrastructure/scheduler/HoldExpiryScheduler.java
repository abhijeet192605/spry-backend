package com.spry.ticket.infrastructure.scheduler;

import com.spry.ticket.domain.port.out.SeatHoldRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldExpiryScheduler {

    private final SeatHoldRepository seatHoldRepository;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "HoldExpiryScheduler", lockAtMostFor = "PT55S", lockAtLeastFor = "PT10S")
    public void expireHolds() {
        int count = seatHoldRepository.updateExpiredHolds();
        if (count > 0) {
            log.info("{} holds expired", count);
        }
        meterRegistry.counter("holds.expired.total").increment(count);
        meterRegistry.summary("hold.expiry.batch.size").record(count);
    }
}
