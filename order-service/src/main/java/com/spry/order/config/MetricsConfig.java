package com.spry.order.config;

import com.spry.order.order.OrderRepository;
import com.spry.order.order.OrderStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Configuration
public class MetricsConfig {

    public MetricsConfig(OrderRepository orderRepository, MeterRegistry meterRegistry) {
        Gauge.builder("orders.pending.stale.count", orderRepository,
                        repo -> repo.findStuckPendingOrders(Instant.now().minus(5, ChronoUnit.MINUTES)).size())
                .description("Number of PENDING orders older than 5 minutes")
                .register(meterRegistry);
    }
}
