package io.github.dancan254.logguard.pattern;

import io.github.dancan254.logguard.BuiltInPattern;
import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.MaskingConfig;
import io.github.dancan254.logguard.PrefilterTrigger;
import io.github.dancan254.logguard.mask.ValueMasker;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PatternMasker {

    private record Rule(MaskStrategy strategy, boolean luhnChecked) {
    }

    private final ValueMasker valueMasker;
    private final Pattern alternation;
    private final Map<String, Rule> rulesByGroup;
    private final Set<PrefilterTrigger> triggers;
    private final boolean alwaysScan;

    public PatternMasker(List<BuiltInPattern> builtIn,
                         List<MaskingConfig.CustomPattern> custom,
                         ValueMasker valueMasker) {
        this.valueMasker = valueMasker;
        this.rulesByGroup = new LinkedHashMap<>();
        this.triggers = EnumSet.noneOf(PrefilterTrigger.class);

        List<String> branches = new ArrayList<>();
        for (BuiltInPattern pattern : builtIn) {
            branches.add("(?<" + pattern.groupName() + ">" + pattern.regex() + ")");
            rulesByGroup.put(pattern.groupName(), new Rule(pattern.strategy(), pattern.isLuhnChecked()));
            triggers.add(pattern.trigger());
        }
        for (int index = 0; index < custom.size(); index++) {
            MaskingConfig.CustomPattern pattern = custom.get(index);
            String group = "CUSTOM" + index;
            branches.add("(?<" + group + ">" + pattern.regex() + ")");
            rulesByGroup.put(group, new Rule(pattern.strategy(), false));
        }

        // A custom regex declares no trigger character, so its presence disables the prefilter
        // rather than risking a false negative.
        this.alwaysScan = !custom.isEmpty();
        this.alternation = branches.isEmpty() ? null : Pattern.compile(String.join("|", branches));
    }

    public String mask(String message) {
        if (alternation == null || message == null || message.isEmpty() || !mightMatch(message)) {
            return message;
        }
        Matcher matcher = alternation.matcher(message);
        if (!matcher.find()) {
            return message;
        }
        StringBuilder masked = new StringBuilder(message.length());
        do {
            matcher.appendReplacement(masked, Matcher.quoteReplacement(replacementFor(matcher)));
        } while (matcher.find());
        matcher.appendTail(masked);
        return masked.toString();
    }

    private String replacementFor(Matcher matcher) {
        String matched = matcher.group();
        for (Map.Entry<String, Rule> entry : rulesByGroup.entrySet()) {
            if (matcher.group(entry.getKey()) == null) {
                continue;
            }
            Rule rule = entry.getValue();
            if (rule.luhnChecked() && !Luhn.isValid(matched)) {
                return matched;
            }
            return valueMasker.mask(matched, rule.strategy());
        }
        return matched;
    }

    private boolean mightMatch(String message) {
        if (alwaysScan) {
            return true;
        }
        for (int index = 0; index < message.length(); index++) {
            char character = message.charAt(index);
            for (PrefilterTrigger trigger : triggers) {
                if (trigger.isPresent(character)) {
                    return true;
                }
            }
        }
        return false;
    }
}
