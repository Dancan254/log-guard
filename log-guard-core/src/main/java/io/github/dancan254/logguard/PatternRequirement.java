package io.github.dancan254.logguard;

/**
 * The least a message must contain before a pattern could possibly match it. Every requirement here
 * has to be one the regex genuinely cannot do without — an over-strict requirement is a false
 * negative, which is a leak.
 *
 * @param requiredCharacter a character the regex cannot match without, or {@code 0} for none
 * @param minimumDigits     digits anywhere in the message, not necessarily adjacent
 * @param minimumUppercase  uppercase ASCII letters anywhere in the message
 */
public record PatternRequirement(char requiredCharacter, int minimumDigits, int minimumUppercase) {

    public boolean isSatisfiedBy(MessageStats stats) {
        if (requiredCharacter != 0 && !stats.contains(requiredCharacter)) {
            return false;
        }
        return stats.digits() >= minimumDigits && stats.uppercase() >= minimumUppercase;
    }
}
