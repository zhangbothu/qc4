package com.mapsearch.qc.train;

import com.mapsearch.qc.text.QueryTokenizer;
import com.mapsearch.qc.text.TokenizedQuery;

/** Maps an expected (corrected) query onto the tokens of the typed query, 1:1. */
public final class GoldAligner {

    private GoldAligner() {
    }

    /** @return normalized gold token per typed token, or null when tokenizations do not line up */
    public static String[] goldTokens(TokenizedQuery typed, String expected) {
        TokenizedQuery gold = QueryTokenizer.tokenize(expected);
        if (gold.size() != typed.size()) {
            return null;
        }
        String[] out = new String[typed.size()];
        for (int i = 0; i < typed.size(); i++) {
            if (gold.tokens().get(i).type() != typed.tokens().get(i).type()) {
                return null;
            }
            out[i] = gold.tokens().get(i).norm();
        }
        return out;
    }
}
