package com.spry.order.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spry.order.config.SecurityConfig;
import com.spry.order.customer.dto.CreateCustomerRequest;
import com.spry.order.customer.dto.CustomerResponse;
import com.spry.order.customer.exception.CustomerNotFoundException;
import com.spry.order.customer.exception.DuplicateEmailException;
import com.spry.order.order.OrderRepository;
import com.spry.order.order.OrderService;
import io.micrometer.core.instrument.MeterRegistry;
import com.spry.shared.exception.GlobalExceptionHandler;
import com.spry.shared.security.JwtService;
import com.spry.shared.security.JwtUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class CustomerControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean CustomerService customerService;
    @MockBean OrderService orderService;
    @MockBean JwtService jwtService;
    @MockBean OrderRepository orderRepository;
    @MockBean MeterRegistry meterRegistry;

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    // ── POST /api/v1/customers ────────────────────────────────────────────────

    @Test
    void createCustomer_returns201_withLocationHeader() throws Exception {
        var req = new CreateCustomerRequest("Alice", "alice@example.com", "123 Main St");
        when(customerService.create(any())).thenReturn(customerResponse(CUSTOMER_ID, "Alice"));

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(asAdmin()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(CUSTOMER_ID.toString())))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void createCustomer_returns409_whenEmailDuplicated() throws Exception {
        var req = new CreateCustomerRequest("Bob", "bob@example.com", null);
        when(customerService.create(any())).thenThrow(new DuplicateEmailException("bob@example.com"));

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(asAdmin()))
                .andExpect(status().isConflict());
    }

    @Test
    void createCustomer_returns400_whenEmailInvalid() throws Exception {
        var req = new CreateCustomerRequest("Alice", "not-an-email", "address");

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(asAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void createCustomer_returns400_whenNameBlank() throws Exception {
        var req = new CreateCustomerRequest("", "alice@example.com", "address");

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(asAdmin()))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/customers/{id} ────────────────────────────────────────────

    @Test
    void getCustomer_returns200_whenFound() throws Exception {
        when(customerService.getById(CUSTOMER_ID))
                .thenReturn(customerResponse(CUSTOMER_ID, "Alice"));

        mockMvc.perform(get("/api/v1/customers/{id}", CUSTOMER_ID)
                        .with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void getCustomer_returns404_whenNotFound() throws Exception {
        when(customerService.getById(CUSTOMER_ID))
                .thenThrow(new CustomerNotFoundException(CUSTOMER_ID));

        mockMvc.perform(get("/api/v1/customers/{id}", CUSTOMER_ID)
                        .with(asAdmin()))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/customers/{id}/orders ─────────────────────────────────────

    @Test
    void listOrders_returns200_withEmptyPage() throws Exception {
        when(orderService.listForCustomer(eq(CUSTOMER_ID), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/customers/{id}/orders", CUSTOMER_ID)
                        .with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private RequestPostProcessor asAdmin() {
        return SecurityMockMvcRequestPostProcessors.user(
                new JwtUser(ADMIN_ID, "admin@spry.io", "ADMIN"));
    }

    private CustomerResponse customerResponse(UUID id, String name) {
        return new CustomerResponse(id, name, name.toLowerCase() + "@example.com",
                "123 Main St", Instant.now());
    }
}
