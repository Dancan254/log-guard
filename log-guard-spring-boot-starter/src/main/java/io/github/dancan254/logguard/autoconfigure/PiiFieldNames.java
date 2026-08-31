package io.github.dancan254.logguard.autoconfigure;

import java.util.Locale;
import java.util.Set;

/**
 * Field names that almost always hold personal data. Matching is on the name alone, which is the
 * only signal available before a single object has been logged.
 */
final class PiiFieldNames {

    private static final Set<String> NAMES = Set.of(
            "email", "emailaddress",
            "phone", "phonenumber", "mobile", "msisdn",
            "ssn", "socialsecuritynumber",
            "nationalid", "idnumber",
            "dob", "dateofbirth",
            "iban", "accountnumber",
            "pan", "cardnumber",
            "password", "passwordhash",
            "token", "secret",
            "firstname", "lastname", "fullname");

    private PiiFieldNames() {
    }

    static boolean isSensitive(String fieldName) {
        return NAMES.contains(normalise(fieldName));
    }

    /** {@code date_of_birth}, {@code dateOfBirth} and {@code DATEOFBIRTH} are one name. */
    private static String normalise(String fieldName) {
        StringBuilder normalised = new StringBuilder(fieldName.length());
        for (int index = 0; index < fieldName.length(); index++) {
            char character = fieldName.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                normalised.append(Character.toLowerCase(character));
            }
        }
        return normalised.toString().toLowerCase(Locale.ROOT);
    }
}
