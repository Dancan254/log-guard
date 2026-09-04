package io.github.dancan254.logguardfixtures.inherited;

import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public class BaseCustomer {

    String email;
    String phoneNumber;
}
