package com.spry.order.customer;

import com.spry.order.customer.dto.CreateCustomerRequest;
import com.spry.order.customer.exception.CustomerNotFoundException;
import com.spry.order.customer.exception.DuplicateEmailException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock CustomerRepository customerRepository;

    CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository);
    }

    @Test
    void create_savesCustomerAndReturnsResponse() {
        var req = new CreateCustomerRequest("Alice Johnson", "alice@example.com", "123 Main St");
        when(customerRepository.existsByEmail(req.email())).thenReturn(false);
        when(customerRepository.save(any())).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            try {
                var f = Customer.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(c, UUID.randomUUID());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return c;
        });

        var response = customerService.create(req);

        assertThat(response.name()).isEqualTo("Alice Johnson");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.address()).isEqualTo("123 Main St");
        assertThat(response.id()).isNotNull();
        verify(customerRepository).save(any());
    }

    @Test
    void create_throwsDuplicateEmail_whenEmailAlreadyRegistered() {
        var req = new CreateCustomerRequest("Bob", "bob@example.com", null);
        when(customerRepository.existsByEmail("bob@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(req))
                .isInstanceOf(DuplicateEmailException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void getById_returnsResponse_whenCustomerExists() {
        UUID id = UUID.randomUUID();
        var customer = customerWithId(id, "Carol", "carol@example.com");
        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));

        var response = customerService.getById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("Carol");
        assertThat(response.email()).isEqualTo("carol@example.com");
    }

    @Test
    void findCustomer_throwsNotFound_whenMissing() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.findCustomer(id))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    private Customer customerWithId(UUID id, String name, String email) {
        var c = new Customer();
        try {
            var f = Customer.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(c, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        c.setName(name);
        c.setEmail(email);
        return c;
    }
}
