package io.github.dancan254.logguard.jackson;

import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.MaskingConfig;
import io.github.dancan254.logguard.Pii;
import io.github.dancan254.logguard.mask.ValueMasker;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.core.JsonGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Honours {@code @Pii} when Jackson serializes, for JSON-encoded logs and for anyone who wants it
 * on an outbound payload.
 *
 * <p>Register it on a mapper you build for that purpose. It is deliberately not auto-registered:
 * adding it to an application's primary mapper would silently change REST responses, which is a
 * spectacular way for a logging library to break someone's API.
 */
public final class LogGuardModule extends SimpleModule {

    private final ValueMasker valueMasker;

    public LogGuardModule() {
        this((String) null);
    }

    public LogGuardModule(String hashSalt) {
        this(new ValueMasker(hashSalt));
    }

    public LogGuardModule(MaskingConfig config) {
        this(new ValueMasker(config.hashSalt()));
    }

    private LogGuardModule(ValueMasker valueMasker) {
        super("log-guard");
        this.valueMasker = valueMasker;
        setSerializerModifier(new PiiAwareModifier(valueMasker));
    }

    public ValueMasker valueMasker() {
        return valueMasker;
    }

    private static final class PiiAwareModifier extends ValueSerializerModifier {

        private final ValueMasker valueMasker;

        private PiiAwareModifier(ValueMasker valueMasker) {
            this.valueMasker = valueMasker;
        }

        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                         BeanDescription.Supplier description,
                                                         List<BeanPropertyWriter> properties) {
            List<BeanPropertyWriter> replaced = new ArrayList<>(properties.size());
            for (BeanPropertyWriter property : properties) {
                Pii annotation = annotationOn(property);
                if (annotation == null) {
                    replaced.add(property);
                    continue;
                }
                if (annotation.strategy() == MaskStrategy.DROP) {
                    continue;
                }
                property.assignSerializer(new MaskingSerializer(valueMasker, annotation.strategy()));
                replaced.add(property);
            }
            return replaced;
        }

        private static Pii annotationOn(BeanPropertyWriter property) {
            AnnotatedMember member = property.getMember();
            return member == null ? null : member.getAnnotation(Pii.class);
        }
    }

    /** Masked values are written as strings whatever the declared type was. */
    private static final class MaskingSerializer extends ValueSerializer<Object> {

        private final ValueMasker valueMasker;
        private final MaskStrategy strategy;

        private MaskingSerializer(ValueMasker valueMasker, MaskStrategy strategy) {
            this.valueMasker = valueMasker;
            this.strategy = strategy;
        }

        @Override
        public void serialize(Object value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(valueMasker.mask(String.valueOf(value), strategy));
        }
    }
}
