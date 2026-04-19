package com.spry.order.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Email @Size(max = 255) String email,
        String address
) {}
