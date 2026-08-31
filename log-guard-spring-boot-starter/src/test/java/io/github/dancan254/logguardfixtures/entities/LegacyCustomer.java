package io.github.dancan254.logguardfixtures.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class LegacyCustomer {

    @Id
    Long id;
    String email;
    String phoneNumber;
    String city;

    @Override
    public String toString() {
        return "LegacyCustomer(id=" + id + ", email=" + email
                + ", phoneNumber=" + phoneNumber + ", city=" + city + ")";
    }
}
