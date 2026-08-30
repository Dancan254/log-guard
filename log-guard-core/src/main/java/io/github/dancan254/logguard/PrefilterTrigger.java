package io.github.dancan254.logguard;

/**
 * The character a pattern cannot match without. A message containing no trigger for any enabled
 * pattern skips the regex entirely, so every pattern must declare one it genuinely requires —
 * an over-narrow trigger here is a false negative, which is a leak.
 */
public enum PrefilterTrigger {

    AT {
        @Override
        public boolean isPresent(char character) {
            return character == '@';
        }
    },

    DIGIT {
        @Override
        public boolean isPresent(char character) {
            return character >= '0' && character <= '9';
        }
    };

    public abstract boolean isPresent(char character);
}
