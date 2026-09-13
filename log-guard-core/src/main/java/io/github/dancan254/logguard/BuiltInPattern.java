package io.github.dancan254.logguard;

/**
 * The patterns the regex layer can apply to an already-formatted message.
 *
 * <p>This layer exists for output from code you do not own — a Hibernate bind-parameter trace, a
 * driver quoting the offending value back in an exception — where there is no annotation to read.
 *
 * <p>Each pattern is guarded by a {@link PatternRequirement} the prefilter checks first, so a line
 * that cannot possibly match never reaches the regex. All four enabled by default are safe against
 * ordinary text; {@link #KENYAN_NATIONAL_ID} is not, and ships disabled.
 */
public enum BuiltInPattern {

    // The lookbehind stops an attempt starting part-way through a run of local-part characters.
    // Without it every position in a long word begins a fresh 64-character attempt, which makes
    // the scan quadratic in the length of the line.
    /** Email addresses. Requires an {@code @} in the line before the regex runs. */
    EMAIL("(?<![A-Za-z0-9._%+\\-])[A-Za-z0-9._%+\\-]{1,64}+@[A-Za-z0-9.\\-]{1,255}\\.[A-Za-z]{2,24}",
            new PatternRequirement('@', 0, 0), MaskStrategy.REDACT, false),

    /** International bank account numbers: two letters, two check digits, then up to 30 more. */
    IBAN("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}+\\b",
            new PatternRequirement((char) 0, 2, 2), MaskStrategy.REDACT, false),

    /** Card numbers of 13 to 19 digits, optionally spaced or hyphenated. Luhn-checked, so an
     * order number of the same length is not mistaken for one. */
    CREDIT_CARD("\\b\\d(?:[ \\-]?\\d){12,18}+\\b",
            new PatternRequirement((char) 0, 13, 0), MaskStrategy.REDACT, true),

    /** Phone numbers in E.164 form: a {@code +} followed by 8 to 15 digits. */
    PHONE_E164("\\+\\d{8,15}+\\b",
            new PatternRequirement('+', 8, 0), MaskStrategy.REDACT, false),

    /**
     * Kenyan national identity numbers, as a run of 7 or 8 digits.
     *
     * <p>Disabled by default, and worth leaving that way unless you know your log lines: a bare
     * digit run of that length is also an order number, a row count and a port.
     */
    KENYAN_NATIONAL_ID("\\b\\d{7,8}+\\b",
            new PatternRequirement((char) 0, 7, 0), MaskStrategy.REDACT, false);

    private final String regex;
    private final PatternRequirement requirement;
    private final MaskStrategy strategy;
    private final boolean luhnChecked;

    BuiltInPattern(String regex, PatternRequirement requirement, MaskStrategy strategy,
                   boolean luhnChecked) {
        this.regex = regex;
        this.requirement = requirement;
        this.strategy = strategy;
        this.luhnChecked = luhnChecked;
    }

    /**
     * @return the regular expression this pattern matches with
     */
    public String regex() {
        return regex;
    }

    /**
     * @return what the prefilter must find in a line before the regex is worth running
     */
    public PatternRequirement requirement() {
        return requirement;
    }

    /**
     * @return the strategy applied to a match
     */
    public MaskStrategy strategy() {
        return strategy;
    }

    /**
     * @return whether a match is confirmed with the Luhn checksum before being masked
     */
    public boolean isLuhnChecked() {
        return luhnChecked;
    }

    /** Named groups may only contain letters and digits, so the underscores go. */
    public String groupName() {
        return name().replace("_", "");
    }
}
