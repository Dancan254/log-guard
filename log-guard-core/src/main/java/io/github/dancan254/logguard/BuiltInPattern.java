package io.github.dancan254.logguard;

public enum BuiltInPattern {

    // The lookbehind stops an attempt starting part-way through a run of local-part characters.
    // Without it every position in a long word begins a fresh 64-character attempt, which makes
    // the scan quadratic in the length of the line.
    EMAIL("(?<![A-Za-z0-9._%+\\-])[A-Za-z0-9._%+\\-]{1,64}+@[A-Za-z0-9.\\-]{1,255}\\.[A-Za-z]{2,24}",
            new PatternRequirement('@', 0, 0), MaskStrategy.REDACT, false),

    IBAN("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}+\\b",
            new PatternRequirement((char) 0, 2, 2), MaskStrategy.REDACT, false),

    CREDIT_CARD("\\b\\d(?:[ \\-]?\\d){12,18}+\\b",
            new PatternRequirement((char) 0, 13, 0), MaskStrategy.REDACT, true),

    PHONE_E164("\\+\\d{8,15}+\\b",
            new PatternRequirement('+', 8, 0), MaskStrategy.REDACT, false),

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

    public String regex() {
        return regex;
    }

    public PatternRequirement requirement() {
        return requirement;
    }

    public MaskStrategy strategy() {
        return strategy;
    }

    public boolean isLuhnChecked() {
        return luhnChecked;
    }

    /** Named groups may only contain letters and digits, so the underscores go. */
    public String groupName() {
        return name().replace("_", "");
    }
}
