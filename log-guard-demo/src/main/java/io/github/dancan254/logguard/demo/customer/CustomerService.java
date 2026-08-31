package io.github.dancan254.logguard.demo.customer;

import io.github.dancan254.logguard.demo.customer.dto.CreateCustomerRequest;
import io.github.dancan254.logguard.demo.customer.dto.CustomerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerResponse register(CreateCustomerRequest request) {
        Customer customer = new Customer(null, request.email(), request.phoneNumber(),
                request.nationalId(), request.dateOfBirth(), request.city());

        Customer saved = customerRepository.save(customer);
        log.info("Registered customer {}", saved);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CustomerResponse findByEmail(String email) {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("no customer for that address"));

        log.info("Looked up customer {}", customer);

        return toResponse(customer);
    }

    private static CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getEmail(), customer.getCity());
    }
}
