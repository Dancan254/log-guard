package io.github.dancan254.logguardfixtures.inherited;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class LombokSubEntity extends LombokBaseEntity {

    @Id
    Long id;
}
