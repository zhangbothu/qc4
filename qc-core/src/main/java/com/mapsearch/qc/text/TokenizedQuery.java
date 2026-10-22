package com.mapsearch.qc.text;

import java.util.List;

/**
 * @param lastTokenIncomplete true when the user is still typing the last token (no trailing separator).
 *                            Such a token is matched as a <em>prefix</em> and must never be completed.
 */
public record TokenizedQuery(String original, List<QToken> tokens, boolean lastTokenIncomplete) {

    public int size() {
        return tokens.size();
    }

    public boolean isPrefixPosition(int i) {
        return lastTokenIncomplete && i == tokens.size() - 1;
    }
}
