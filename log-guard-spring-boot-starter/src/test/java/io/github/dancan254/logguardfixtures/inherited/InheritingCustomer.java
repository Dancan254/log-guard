package io.github.dancan254.logguardfixtures.inherited;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** The toString is on the subclass, the personal data on the parent — the usual JPA layout. */
@Entity
public class InheritingCustomer extends BaseCustomer {

    @Id
    Long id;
    String city;

    @Override
    public String toString() {
        return "InheritingCustomer(id=" + id + ", email=" + email
                + ", phoneNumber=" + phoneNumber + ", city=" + city + ")";
    }
}
