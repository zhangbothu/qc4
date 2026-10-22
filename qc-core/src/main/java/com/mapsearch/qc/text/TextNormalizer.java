package com.mapsearch.qc.text;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Folds a surface token into the key space of the token dictionary
 * (lower case, ASCII a-z only, no accents / apostrophes).
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String fold(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String nfkd = Normalizer.normalize(s, Normalizer.Form.NFKD);
        StringBuilder sb = new StringBuilder(nfkd.length());
        for (int i = 0; i < nfkd.length(); i++) {
            char c = nfkd.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) {
                continue;
            }
            switch (c) {
                case 'ß' -> sb.append("ss");
                case 'æ', 'Æ' -> sb.append("ae");
                case 'œ', 'Œ' -> sb.append("oe");
                case 'ø', 'Ø' -> sb.append('o');
                case 'ł', 'Ł' -> sb.append('l');
                case 'đ', 'Đ' -> sb.append('d');
                case 'ı' -> sb.append('i');
                case '\'', '’', '`', '´' -> {
                }
                default -> sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    public static boolean isAsciiLetters(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 'a' || c > 'z') {
                return false;
            }
        }
        return true;
    }

    /** Splits a POI name / address into dictionary-space tokens (used offline and by the click miner). */
    public static java.util.List<String> dictTokens(String text) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (QToken t : QueryTokenizer.tokenize(text).tokens()) {
            if (t.type() == TokenType.WORD) {
                out.add(t.norm());
            }
        }
        return out;
    }
}
