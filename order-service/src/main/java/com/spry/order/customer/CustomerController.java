package com.spry.order.customer;

import com.spry.order.customer.dto.CreateCustomerRequest;
import com.spry.order.customer.dto.CustomerResponse;
import com.spry.order.order.OrderService;
import com.spry.order.order.dto.OrderSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest req) {
        var response = customerService.create(req);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public CustomerResponse getCustomer(@PathVariable UUID id) {
        return customerService.getById(id);
    }

    @GetMapping("/{customerId}/orders")
    public Page<OrderSummaryResponse> listOrders(
            @PathVariable UUID customerId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "orderDate", direction = Sort.Direction.DESC) Pageable pageable) {

        return orderService.listForCustomer(customerId, status, pageable);
    }
}
