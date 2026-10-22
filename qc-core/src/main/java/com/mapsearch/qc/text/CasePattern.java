package com.mapsearch.qc.text;

import java.util.Locale;

/** Re-applies the casing style of what the user typed to a corrected (lower-case) token. */
public final class CasePattern {

    private CasePattern() {
    }

    public static String apply(String originalRaw, String correctedLower) {
        if (correctedLower.isEmpty() || originalRaw.isEmpty()) {
            return correctedLower;
        }
        boolean allUpper = originalRaw.length() > 1 && originalRaw.equals(originalRaw.toUpperCase(Locale.ROOT))
                && !originalRaw.equals(originalRaw.toLowerCase(Locale.ROOT));
        if (allUpper) {
            return correctedLower.toUpperCase(Locale.ROOT);
        }
        if (Character.isUpperCase(originalRaw.charAt(0))) {
            StringBuilder sb = new StringBuilder(correctedLower.length());
            boolean startOfWord = true;
            for (int i = 0; i < correctedLower.length(); i++) {
                char c = correctedLower.charAt(i);
                sb.append(startOfWord ? Character.toUpperCase(c) : c);
                startOfWord = c == ' ';
            }
            return sb.toString();
        }
        return correctedLower;
    }
}
