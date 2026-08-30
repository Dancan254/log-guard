package io.github.dancan254.logguard;

public enum BuiltInPattern {

    EMAIL("[A-Za-z0-9._%+\\-]{1,64}@[A-Za-z0-9.\\-]{1,255}\\.[A-Za-z]{2,24}",
            PrefilterTrigger.AT, MaskStrategy.REDACT, false),

    IBAN("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b",
            PrefilterTrigger.DIGIT, MaskStrategy.REDACT, false),

    CREDIT_CARD("\\b\\d(?:[ \\-]?\\d){12,18}\\b",
            PrefilterTrigger.DIGIT, MaskStrategy.REDACT, true),

    PHONE_E164("\\+\\d{8,15}\\b",
            PrefilterTrigger.DIGIT, MaskStrategy.REDACT, false),

    KENYAN_NATIONAL_ID("\\b\\d{7,8}\\b",
            PrefilterTrigger.DIGIT, MaskStrategy.REDACT, false);

    private final String regex;
    private final PrefilterTrigger trigger;
    private final MaskStrategy strategy;
    private final boolean luhnChecked;

    BuiltInPattern(String regex, PrefilterTrigger trigger, MaskStrategy strategy, boolean luhnChecked) {
        this.regex = regex;
        this.trigger = trigger;
        this.strategy = strategy;
        this.luhnChecked = luhnChecked;
    }

    public String regex() {
        return regex;
    }

    public PrefilterTrigger trigger() {
        return trigger;
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
