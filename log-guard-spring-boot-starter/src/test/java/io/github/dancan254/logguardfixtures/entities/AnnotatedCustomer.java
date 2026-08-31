package io.github.dancan254.logguardfixtures.entities;

import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.Pii;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class AnnotatedCustomer {

    @Id
    Long id;

    @Pii(strategy = MaskStrategy.HASH)
    String email;

    @Pii(strategy = MaskStrategy.DROP)
    String password;

    @Override
    public String toString() {
        return "AnnotatedCustomer(id=" + id + ")";
    }
}
