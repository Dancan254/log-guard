package io.github.dancan254.logguard.demo.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

public record CreateCustomerRequest(

        @NotBlank @Email String email,

        @NotBlank String phoneNumber,

        @NotBlank String nationalId,

        @NotNull @Past LocalDate dateOfBirth,

        String city) {
}
