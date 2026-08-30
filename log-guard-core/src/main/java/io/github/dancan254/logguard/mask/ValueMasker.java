package io.github.dancan254.logguard.mask;

import io.github.dancan254.logguard.MaskStrategy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ValueMasker {

    private static final String REDACTED = "***";

    /**
     * Fixed width on purpose. A mask that grows with the value leaks its length, which for a
     * national ID or a card number is most of what an attacker needed.
     */
    private static final String MASK = "****";

    private static final int PARTIAL_HEAD_MINIMUM_LENGTH = 12;
    private static final int PARTIAL_TAIL_MINIMUM_LENGTH = 8;
    private static final int HEAD_LENGTH = 5;
    private static final int TAIL_LENGTH = 3;
    private static final int DIGEST_BYTES = 3;

    private final String hashSalt;

    public ValueMasker(String hashSalt) {
        this.hashSalt = hashSalt;
    }

    public String mask(String value, MaskStrategy strategy) {
        if (value == null) {
            return REDACTED;
        }
        return switch (strategy) {
            case REDACT -> REDACTED;
            case PARTIAL -> partial(value);
            case HASH -> hash(value);
            // DROP is a field-level decision the renderer makes; redacting is the safe fallback.
            case DROP -> REDACTED;
        };
    }

    public boolean hasHashSalt() {
        return hashSalt != null && !hashSalt.isBlank();
    }

    private String partial(String value) {
        int atSign = value.indexOf('@');
        if (atSign > 0 && atSign < value.length() - 1) {
            return value.charAt(0) + MASK + value.substring(atSign);
        }
        int length = value.length();
        if (length >= PARTIAL_HEAD_MINIMUM_LENGTH) {
            return value.substring(0, HEAD_LENGTH) + MASK + value.substring(length - TAIL_LENGTH);
        }
        if (length >= PARTIAL_TAIL_MINIMUM_LENGTH) {
            return MASK + value.substring(length - TAIL_LENGTH);
        }
        return REDACTED;
    }

    private String hash(String value) {
        if (!hasHashSalt()) {
            return REDACTED;
        }
        MessageDigest digest = sha256();
        digest.update(hashSalt.getBytes(StandardCharsets.UTF_8));
        byte[] output = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        return "#" + HexFormat.of().formatHex(output, 0, DIGEST_BYTES);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException cause) {
            throw new IllegalStateException("SHA-256 is required of every JDK", cause);
        }
    }
}
