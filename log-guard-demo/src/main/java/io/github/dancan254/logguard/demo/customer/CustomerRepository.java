package io.github.dancan254.logguard.demo.customer;

import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;

public interface CustomerRepository extends ListCrudRepository<Customer, Long> {

    Optional<Customer> findByEmail(String email);
}
