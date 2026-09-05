package io.github.dancan254.logguardfixtures.inherited;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Inherits the only HASH field, so a missing salt has to be caught through the parent. */
@Entity
public class HashingEntity extends HashingCustomer {

    @Id
    Long id;
}
