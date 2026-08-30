package io.github.dancan254.logguard.meta;

import java.util.List;

public record PiiMetadata(List<PiiField> fields, boolean hasPii) {

    static final PiiMetadata NONE = new PiiMetadata(List.of(), false);

    public PiiMetadata {
        fields = List.copyOf(fields);
    }
}
