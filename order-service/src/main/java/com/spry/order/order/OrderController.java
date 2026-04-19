package com.spry.order.order;

import com.spry.order.order.dto.CreateOrderRequest;
import com.spry.order.order.dto.OrderResponse;
import com.spry.order.order.dto.UpdateStatusRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest req) {
        var response = orderService.create(req);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable UUID id) {
        return orderService.getById(id);
    }

    @PatchMapping("/{id}/status")
    public OrderResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest req) {

        return orderService.updateStatus(id, req, null);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID id) {
        orderService.delete(id, null);
        return ResponseEntity.noContent().build();
    }
}
