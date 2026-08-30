package io.github.dancan254.logguard.pattern;

final class Luhn {

    private Luhn() {
    }

    /** Turns a rule that eats every 16-digit order ID into one that only eats card numbers. */
    static boolean isValid(String candidate) {
        int sum = 0;
        int digits = 0;
        boolean doubling = false;

        for (int index = candidate.length() - 1; index >= 0; index--) {
            char character = candidate.charAt(index);
            if (character < '0' || character > '9') {
                continue;
            }
            int digit = character - '0';
            if (doubling) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            digits++;
            doubling = !doubling;
        }
        return digits >= 13 && sum % 10 == 0;
    }
}
