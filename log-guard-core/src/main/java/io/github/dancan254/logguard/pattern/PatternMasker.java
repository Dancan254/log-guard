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
import io.github.dancan254.logguard.exception.InvalidPatternException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
        Pattern compiled = branchesByGroup.isEmpty() ? null : compile(branchesByGroup);
        for (int index = 0; index < custom.size(); index++) {
            MaskingConfig.CustomPattern pattern = custom.get(index);
            String group = "CUSTOM" + index;
            // Compiled alone first: the combined alternation reports an offset into itself, which
            // names neither the property at fault nor anything the reader can act on.
            try {
                Pattern.compile(pattern.regex());
            } catch (PatternSyntaxException cause) {
                throw new InvalidPatternException(pattern.name(), pattern.regex(), cause.getDescription());
            }
            branchesByGroup.put(group, pattern.regex());
            rules.add(new Rule(group, pattern.strategy(), false));
            // Validate the combined alternation after each addition so that named-group collisions
            // with built-ins are reported against the custom pattern that introduced them.
            try {
                compiled = compile(branchesByGroup);
            } catch (PatternSyntaxException cause) {
                throw new InvalidPatternException(pattern.name(), pattern.regex(), cause.getDescription());
            }
        }

        // A custom regex declares no trigger character, so the regex must still run when the
        // built-in prefilter finds nothing. The prefilter stays active for built-ins.
        this.alwaysScan = !custom.isEmpty();
        this.alternation = compiled;
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
        int cut = headLength(message);
        if (cut == maxMessageLength) {
            // No separator before the cap: a token crosses the boundary. Extending the scan to the
            // next separator lets the regex see the whole token, so a partial match cannot be
            // printed raw.
            cut = nextWhitespace(message, maxMessageLength);
        }
        String masked = maskAll(message.substring(0, cut));
        if (masked.length() > maxMessageLength) {
            masked = masked.substring(0, maxMessageLength);
        }
        return masked + TRUNCATION_NOTICE;
    }

    /**
     * Cutting at the cap can split an address or a card number, and half a token matches no pattern
     * and is printed raw. The cut moves back to the last separator so the scanned head holds only
     * whole tokens; with none in reach the scan window is extended forward instead.
     */
    private int headLength(String message) {
        for (int index = maxMessageLength; index > 0; index--) {
            if (Character.isWhitespace(message.charAt(index))) {
                return index;
            }
        }
        return maxMessageLength;
    }

    private int nextWhitespace(String message, int start) {
        for (int index = start; index < message.length(); index++) {
            if (Character.isWhitespace(message.charAt(index))) {
                return index;
            }
        }
        return message.length();
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
     * digits in it cannot hold a card number, and most log lines are that line. When custom patterns
     * are configured the pass still runs for built-ins, and the regex falls back to scanning for
     * the custom patterns if no built-in could match.
     */
    private boolean mightMatch(String message) {
        MessageStats stats = MessageStats.of(message);
        for (PatternRequirement requirement : requirements) {
            if (requirement.isSatisfiedBy(stats)) {
                return true;
            }
        }
        return alwaysScan;
    }
}
