package com.mapsearch.qc.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Whitespace / punctuation tokenizer that keeps character offsets so corrections can be spliced back
 * into the original string without disturbing anything the user typed outside the corrected token.
 * Apostrophes inside a word are kept in the raw token and dropped from the key ("McDonald's" -> "mcdonalds").
 */
public final class QueryTokenizer {

    private QueryTokenizer() {
    }

    public static TokenizedQuery tokenize(String query) {
        String q = query == null ? "" : query;
        List<QToken> tokens = new ArrayList<>();
        int i = 0;
        int n = q.length();
        while (i < n) {
            while (i < n && isSeparator(q, i)) {
                i++;
            }
            if (i >= n) {
                break;
            }
            int start = i;
            while (i < n && !isSeparator(q, i)) {
                i++;
            }
            String raw = q.substring(start, i);
            tokens.add(classify(raw, start, i));
        }
        boolean typingApostrophe = n > 1 && (q.charAt(n - 1) == '\'' || q.charAt(n - 1) == '’')
                && Character.isLetter(q.charAt(n - 2));
        boolean incomplete = !tokens.isEmpty() && n > 0 && (!isSeparator(q, n - 1) || typingApostrophe)
                && tokens.get(tokens.size() - 1).type() == TokenType.WORD;
        return new TokenizedQuery(q, List.copyOf(tokens), incomplete);
    }

    private static boolean isSeparator(String q, int i) {
        char c = q.charAt(i);
        if (Character.isLetterOrDigit(c)) {
            return false;
        }
        if (Character.getType(c) == Character.NON_SPACING_MARK) {
            return false;
        }
        if ((c == '\'' || c == '’') && i > 0 && i + 1 < q.length()
                && Character.isLetter(q.charAt(i - 1)) && Character.isLetter(q.charAt(i + 1))) {
            return false;
        }
        return true;
    }

    private static QToken classify(String raw, int start, int end) {
        boolean hasDigit = false;
        boolean hasLatin = false;
        boolean hasOther = false;
        for (int k = 0; k < raw.length(); k++) {
            char c = raw.charAt(k);
            if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (Character.isLetter(c)) {
                Character.UnicodeScript script = Character.UnicodeScript.of(c);
                if (script == Character.UnicodeScript.LATIN) {
                    hasLatin = true;
                } else {
                    hasOther = true;
                }
            }
        }
        TokenType type;
        if (hasOther) {
            type = TokenType.OTHER;
        } else if (hasDigit && hasLatin) {
            type = TokenType.MIXED;
        } else if (hasDigit) {
            type = TokenType.NUMERIC;
        } else {
            type = TokenType.WORD;
        }
        String norm = type == TokenType.WORD ? TextNormalizer.fold(raw) : raw;
        if (type == TokenType.WORD && !TextNormalizer.isAsciiLetters(norm)) {
            type = TokenType.OTHER;
        }
        return new QToken(raw, norm, start, end, type);
    }
}
