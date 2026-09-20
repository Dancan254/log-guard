package io.github.dancan254.logguardfixtures.inherited;

import jakarta.persistence.MappedSuperclass;
import lombok.ToString;

@ToString
@MappedSuperclass
public class LombokBaseEntity {

    String email;
}
