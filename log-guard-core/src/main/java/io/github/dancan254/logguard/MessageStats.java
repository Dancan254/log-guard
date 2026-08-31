package io.github.dancan254.logguard;

/**
 * What a single pass over a message found. Counting is far cheaper than running an alternation of
 * regexes, and most log lines fail every requirement.
 */
public final class MessageStats {

    private final int digits;
    private final int uppercase;
    private final boolean at;
    private final boolean plus;

    private MessageStats(int digits, int uppercase, boolean at, boolean plus) {
        this.digits = digits;
        this.uppercase = uppercase;
        this.at = at;
        this.plus = plus;
    }

    public static MessageStats of(String message) {
        int digits = 0;
        int uppercase = 0;
        boolean at = false;
        boolean plus = false;
        for (int index = 0; index < message.length(); index++) {
            char character = message.charAt(index);
            if (character >= '0' && character <= '9') {
                digits++;
            } else if (character >= 'A' && character <= 'Z') {
                uppercase++;
            } else if (character == '@') {
                at = true;
            } else if (character == '+') {
                plus = true;
            }
        }
        return new MessageStats(digits, uppercase, at, plus);
    }

    public int digits() {
        return digits;
    }

    public int uppercase() {
        return uppercase;
    }

    public boolean contains(char character) {
        return switch (character) {
            case '@' -> at;
            case '+' -> plus;
            default -> true;
        };
    }
}
