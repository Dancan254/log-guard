package io.github.dancan254.logguard.demo.customer;

import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.Pii;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "customer")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Pii(strategy = MaskStrategy.HASH)
    @Column(nullable = false, unique = true)
    private String email;

    @Pii(strategy = MaskStrategy.PARTIAL)
    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Pii
    @Column(name = "national_id", nullable = false)
    private String nationalId;

    @Pii
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    private String city;
}
