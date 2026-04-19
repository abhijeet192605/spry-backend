package com.spry.ticket.scheduler;

import com.spry.ticket.domain.port.out.SeatHoldRepository;
import com.spry.ticket.infrastructure.scheduler.HoldExpiryScheduler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldExpirySchedulerTest {

    @Mock SeatHoldRepository seatHoldRepository;

    HoldExpiryScheduler scheduler;
    SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new HoldExpiryScheduler(seatHoldRepository, meterRegistry);
    }

    // ── expireHolds ───────────────────────────────────────────────────────────

    @Test
    void expireHolds_callsUpdateExpiredHolds() {
        when(seatHoldRepository.updateExpiredHolds()).thenReturn(0);

        scheduler.expireHolds();

        verify(seatHoldRepository).updateExpiredHolds();
    }

    @Test
    void expireHolds_doesNothing_whenNoHoldsExpired() {
        when(seatHoldRepository.updateExpiredHolds()).thenReturn(0);

        scheduler.expireHolds();

        double expired = meterRegistry.counter("holds.expired.total").count();
        assertThat(expired).isEqualTo(0.0);
    }

    @Test
    void expireHolds_incrementsMetric_byNumberOfExpiredHolds() {
        when(seatHoldRepository.updateExpiredHolds()).thenReturn(5);

        scheduler.expireHolds();

        double expired = meterRegistry.counter("holds.expired.total").count();
        assertThat(expired).isEqualTo(5.0);
    }

    @Test
    void expireHolds_recordsBatchSizeInSummary() {
        when(seatHoldRepository.updateExpiredHolds()).thenReturn(3);

        scheduler.expireHolds();

        double totalAmount = meterRegistry.summary("hold.expiry.batch.size").totalAmount();
        assertThat(totalAmount).isEqualTo(3.0);
    }

    @Test
    void expireHolds_isIdempotent_withMultipleRuns() {
        when(seatHoldRepository.updateExpiredHolds()).thenReturn(2);

        scheduler.expireHolds();
        scheduler.expireHolds();
        scheduler.expireHolds();

        double expired = meterRegistry.counter("holds.expired.total").count();
        assertThat(expired).isEqualTo(6.0);
    }
}
