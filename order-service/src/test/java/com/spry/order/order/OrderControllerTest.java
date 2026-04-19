package com.spry.order.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spry.order.config.SecurityConfig;
import com.spry.order.order.dto.CreateOrderRequest;
import com.spry.order.order.dto.OrderItemRequest;
import com.spry.order.order.dto.OrderItemResponse;
import com.spry.order.order.dto.OrderResponse;
import com.spry.order.order.dto.UpdateStatusRequest;
import com.spry.order.order.OrderRepository;
import com.spry.order.order.exception.ConcurrentOrderModificationException;
import io.micrometer.core.instrument.MeterRegistry;
import com.spry.order.order.exception.InvalidStatusTransitionException;
import com.spry.order.order.exception.OrderNotDeletableException;
import com.spry.order.order.exception.OrderNotFoundException;
import com.spry.shared.exception.GlobalExceptionHandler;
import com.spry.shared.security.JwtService;
import com.spry.shared.security.JwtUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean OrderService orderService;
    @MockBean JwtService jwtService;
    @MockBean OrderRepository orderRepository;
    @MockBean MeterRegistry meterRegistry;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    // ── POST /api/v1/orders ───────────────────────────────────────────────────

    @Test
    void createOrder_returns202_withPendingStatus() throws Exception {
        when(orderService.create(any())).thenReturn(orderResponse(OrderStatus.PENDING));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderRequestJson())
                        .with(asCustomer()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.totalValue").isEmpty());
    }

    @Test
    void createOrder_returns400_whenItemsIsEmpty() throws Exception {
        var req = new CreateOrderRequest(CUSTOMER_ID, Instant.now(), List.of());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(asCustomer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void createOrder_returns400_whenCustomerIdMissing() throws Exception {
        String body = "{\"orderDate\":\"2026-04-19T10:00:00Z\",\"items\":[{\"productName\":\"Book\",\"unitPrice\":10,\"quantity\":1}]}";

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(asCustomer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_returns400_whenItemQuantityIsZero() throws Exception {
        var req = new CreateOrderRequest(CUSTOMER_ID, Instant.now(),
                List.of(new OrderItemRequest("Book", new BigDecimal("10.00"), 0)));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(asCustomer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    // ── GET /api/v1/orders/{id} ───────────────────────────────────────────────

    @Test
    void getOrder_returns200_whenFound() throws Exception {
        when(orderService.getById(eq(ORDER_ID))).thenReturn(orderResponse(OrderStatus.CONFIRMED));

        mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID)
                        .with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void getOrder_returns404_whenNotFound() throws Exception {
        when(orderService.getById(eq(ORDER_ID)))
                .thenThrow(new OrderNotFoundException(ORDER_ID));

        mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID)
                        .with(asAdmin()))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /api/v1/orders/{id}/status ─────────────────────────────────────

    @Test
    void updateStatus_returns200_onValidTransition() throws Exception {
        when(orderService.updateStatus(eq(ORDER_ID), any(), any()))
                .thenReturn(orderResponse(OrderStatus.SHIPPED));

        mockMvc.perform(patch("/api/v1/orders/{id}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateStatusRequest(OrderStatus.SHIPPED, 0L)))
                        .with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void updateStatus_returns409_whenVersionMismatch() throws Exception {
        when(orderService.updateStatus(eq(ORDER_ID), any(), any()))
                .thenThrow(new ConcurrentOrderModificationException(ORDER_ID));

        mockMvc.perform(patch("/api/v1/orders/{id}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateStatusRequest(OrderStatus.SHIPPED, 99L)))
                        .with(asAdmin()))
                .andExpect(status().isConflict());
    }

    @Test
    void updateStatus_returns400_whenTransitionInvalid() throws Exception {
        when(orderService.updateStatus(eq(ORDER_ID), any(), any()))
                .thenThrow(new InvalidStatusTransitionException(OrderStatus.PENDING, OrderStatus.CONFIRMED));

        mockMvc.perform(patch("/api/v1/orders/{id}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateStatusRequest(OrderStatus.CONFIRMED, 0L)))
                        .with(asAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_returns400_whenStatusMissing() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/{id}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}")
                        .with(asAdmin()))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /api/v1/orders/{id} ────────────────────────────────────────────

    @Test
    void deleteOrder_returns204_onSuccess() throws Exception {
        doNothing().when(orderService).delete(eq(ORDER_ID), any());

        mockMvc.perform(delete("/api/v1/orders/{id}", ORDER_ID)
                        .with(asAdmin()))
                .andExpect(status().isNoContent());

        verify(orderService).delete(eq(ORDER_ID), any());
    }

    @Test
    void deleteOrder_returns404_whenOrderMissing() throws Exception {
        when(orderService.getById(eq(ORDER_ID)))
                .thenThrow(new OrderNotFoundException(ORDER_ID));

        // delete calls orderService.delete internally (not getById), so mock that
        org.mockito.Mockito.doThrow(new OrderNotFoundException(ORDER_ID))
                .when(orderService).delete(eq(ORDER_ID), any());

        mockMvc.perform(delete("/api/v1/orders/{id}", ORDER_ID)
                        .with(asAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteOrder_returns409_whenOrderIsShipped() throws Exception {
        org.mockito.Mockito.doThrow(new OrderNotDeletableException(ORDER_ID))
                .when(orderService).delete(eq(ORDER_ID), any());

        mockMvc.perform(delete("/api/v1/orders/{id}", ORDER_ID)
                        .with(asAdmin()))
                .andExpect(status().isConflict());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private RequestPostProcessor asAdmin() {
        return SecurityMockMvcRequestPostProcessors.user(
                new JwtUser(ADMIN_ID, "admin@spry.io", "ADMIN"));
    }

    private RequestPostProcessor asCustomer() {
        return SecurityMockMvcRequestPostProcessors.user(
                new JwtUser(CUSTOMER_ID, "customer@spry.io", "CUSTOMER"));
    }

    private OrderResponse orderResponse(OrderStatus status) {
        var item = new OrderItemResponse(UUID.randomUUID(), "Clean Code",
                new BigDecimal("39.99"), 1, new BigDecimal("39.99"));
        return new OrderResponse(ORDER_ID, CUSTOMER_ID, status, null,
                Instant.now(), 0L, List.of(item), Instant.now(), Instant.now());
    }

    private String orderRequestJson() throws Exception {
        var req = new CreateOrderRequest(CUSTOMER_ID, Instant.now(),
                List.of(new OrderItemRequest("Clean Code", new BigDecimal("39.99"), 1)));
        return objectMapper.writeValueAsString(req);
    }
}
