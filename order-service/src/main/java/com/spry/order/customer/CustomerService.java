package com.spry.order.customer;

import com.spry.order.customer.dto.CreateCustomerRequest;
import com.spry.order.customer.dto.CustomerResponse;
import com.spry.order.customer.exception.CustomerNotFoundException;
import com.spry.order.customer.exception.DuplicateEmailException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional
    public CustomerResponse create(CreateCustomerRequest req) {
        if (customerRepository.existsByEmail(req.email())) {
            throw new DuplicateEmailException(req.email());
        }

        var customer = new Customer();
        customer.setName(req.name());
        customer.setEmail(req.email());
        customer.setAddress(req.address());
        customerRepository.save(customer);

        log.info("Customer registered: id={}, email={}", customer.getId(), customer.getEmail());
        return toResponse(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(UUID id) {
        return toResponse(findCustomer(id));
    }

    public Customer findCustomer(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getName(), c.getEmail(), c.getAddress(), c.getCreatedAt());
    }
}
