package io.github.dancan254.logguard.pattern;

import io.github.dancan254.logguard.BuiltInPattern;
import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.MaskingConfig;
import io.github.dancan254.logguard.MessageStats;
import io.github.dancan254.logguard.PatternRequirement;
import io.github.dancan254.logguard.mask.ValueMasker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PatternMasker {

    static final String TRUNCATION_NOTICE = "…[log-guard: message truncated]";

    private record Rule(String group, MaskStrategy strategy, boolean luhnChecked) {
    }

    private final ValueMasker valueMasker;
    private final Pattern alternation;
    private final List<Rule> rules;
    private final List<PatternRequirement> requirements;
    private final boolean alwaysScan;
    private final int maxMessageLength;

    public PatternMasker(List<BuiltInPattern> builtIn,
                         List<MaskingConfig.CustomPattern> custom,
                         ValueMasker valueMasker) {
        this(builtIn, custom, valueMasker, MaskingConfig.DEFAULT_MAX_MESSAGE_LENGTH);
    }

    public PatternMasker(List<BuiltInPattern> builtIn,
                         List<MaskingConfig.CustomPattern> custom,
                         ValueMasker valueMasker,
                         int maxMessageLength) {
        this.valueMasker = valueMasker;
        this.maxMessageLength = maxMessageLength;
        this.requirements = new ArrayList<>();
        this.rules = new ArrayList<>();

        Map<String, String> branchesByGroup = new LinkedHashMap<>();
        for (BuiltInPattern pattern : builtIn) {
            branchesByGroup.put(pattern.groupName(), pattern.regex());
            rules.add(new Rule(pattern.groupName(), pattern.strategy(), pattern.isLuhnChecked()));
            requirements.add(pattern.requirement());
        }
        for (int index = 0; index < custom.size(); index++) {
            MaskingConfig.CustomPattern pattern = custom.get(index);
            String group = "CUSTOM" + index;
            branchesByGroup.put(group, pattern.regex());
            rules.add(new Rule(group, pattern.strategy(), false));
        }

        // A custom regex declares no trigger character, so its presence disables the prefilter
        // rather than risking a false negative.
        this.alwaysScan = !custom.isEmpty();
        this.alternation = branchesByGroup.isEmpty() ? null : compile(branchesByGroup);
    }

    private static Pattern compile(Map<String, String> branchesByGroup) {
        StringBuilder alternation = new StringBuilder();
        branchesByGroup.forEach((group, regex) -> {
            if (!alternation.isEmpty()) {
                alternation.append('|');
            }
            alternation.append("(?<").append(group).append('>').append(regex).append(')');
        });
        return Pattern.compile(alternation.toString());
    }

    public String mask(String message) {
        if (alternation == null || message == null || message.isEmpty()) {
            return message;
        }
        if (message.length() > maxMessageLength) {
            return maskWithinLimit(message);
        }
        return maskAll(message);
    }

    /**
     * Fails closed. Skipping the regex on a long message would be a leak anyone can trigger by
     * padding a field, so the head is masked and the unexamined tail is dropped.
     */
    private String maskWithinLimit(String message) {
        return maskAll(message.substring(0, maxMessageLength)) + TRUNCATION_NOTICE;
    }

    private String maskAll(String message) {
        if (!mightMatch(message)) {
            return message;
        }
        Matcher matcher = alternation.matcher(message);
        if (!matcher.find()) {
            return message;
        }
        StringBuilder masked = new StringBuilder(message.length());
        do {
            matcher.appendReplacement(masked, escaped(replacementFor(matcher)));
        } while (matcher.find());
        matcher.appendTail(masked);
        return masked.toString();
    }

    /** Masks are plain text almost always; quoting only when they are not saves a copy per match. */
    private static String escaped(String replacement) {
        for (int index = 0; index < replacement.length(); index++) {
            char character = replacement.charAt(index);
            if (character == '$' || character == '\\') {
                return Matcher.quoteReplacement(replacement);
            }
        }
        return replacement;
    }

    private String replacementFor(Matcher matcher) {
        String matched = matcher.group();
        for (Rule rule : rules) {
            if (matcher.start(rule.group()) < 0) {
                continue;
            }
            if (rule.luhnChecked() && !Luhn.isValid(matched)) {
                return matched;
            }
            return valueMasker.mask(matched, rule.strategy());
        }
        return matched;
    }

    /**
     * One counting pass decides whether any enabled pattern could match at all. A line with three
     * digits in it cannot hold a card number, and most log lines are that line.
     */
    private boolean mightMatch(String message) {
        if (alwaysScan) {
            return true;
        }
        MessageStats stats = MessageStats.of(message);
        for (PatternRequirement requirement : requirements) {
            if (requirement.isSatisfiedBy(stats)) {
                return true;
            }
        }
        return false;
    }
}
