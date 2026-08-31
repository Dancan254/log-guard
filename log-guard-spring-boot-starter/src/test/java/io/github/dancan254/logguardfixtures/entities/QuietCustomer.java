package io.github.dancan254.logguardfixtures.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** No toString: nothing of it has ever reached a log. */
@Entity
public class QuietCustomer {

    @Id
    Long id;
    String email;
}
