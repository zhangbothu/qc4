package com.mapsearch.qc.text;

import org.apache.commons.codec.language.DoubleMetaphone;

/**
 * Double Metaphone codes, the same family of codes stored in the {@code tone} field of poi_correcton_v2.
 * Thread-safe: DoubleMetaphone keeps no mutable state after construction.
 */
public final class Phonetic {

    private static final DoubleMetaphone DM = new DoubleMetaphone();

    static {
        DM.setMaxCodeLen(8);
    }

    private Phonetic() {
    }

    public static String primary(String word) {
        String c = DM.doubleMetaphone(word, false);
        return c == null ? "" : c;
    }

    public static String alternate(String word) {
        String c = DM.doubleMetaphone(word, true);
        return c == null ? "" : c;
    }

    public static boolean soundsAlike(String a, String b) {
        String pa = primary(a);
        if (pa.isEmpty()) {
            return false;
        }
        String pb = primary(b);
        return pa.equals(pb) || pa.equals(alternate(b)) || alternate(a).equals(pb);
    }

    /** Pairs of letters that are commonly confused because they sound alike in ES / EN / MS / ID. */
    public static boolean phoneticPair(char a, char b) {
        if (a == b) {
            return false;
        }
        String p = a < b ? "" + a + b : "" + b + a;
        return switch (p) {
            case "ck", "cs", "kq", "cq", "sz", "cz", "bv", "iy", "fv", "gj", "hj", "jy", "ks", "uw", "ou",
                 "ei", "ae", "mn", "dt", "bp", "lr", "uy", "xs", "gk" -> true;
            default -> false;
        };
    }
}
