package io.github.dancan254.logguardfixtures.inherited;

import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.Pii;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public class HashingCustomer {

    @Pii(strategy = MaskStrategy.HASH)
    String email;
}
