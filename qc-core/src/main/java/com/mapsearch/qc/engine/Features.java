package com.mapsearch.qc.engine;

/**
 * Feature space of the log-linear path score. Node features describe one candidate for one token span;
 * edge features describe the transition between two adjacent spans (context). A path's feature vector is
 * the sum of its node and edge vectors, so the same weight vector scores both and can be learned with a
 * structured perceptron.
 */
public final class Features {

    private Features() {
    }

    /** 1 when the candidate changes the token. Learns the global "don't touch" prior. */
    public static final int CHANGE = 0;
    /** -cost of the weighted (learned) channel model, i.e. log P(typed | candidate). */
    public static final int CHANNEL = 1;
    public static final int EDITS = 2;
    public static final int EDIT_RATIO = 3;
    /** log of the candidate's popularity share (word weight, or prefix subtree mass for the token being typed). */
    public static final int LOG_FREQ = 4;
    /** Candidate is neither a dictionary word nor a legal prefix. */
    public static final int OOV = 5;
    public static final int CITY_FREQ = 6;
    public static final int CITY_SPREAD = 7;
    public static final int EXPOSE = 8;
    public static final int ERRDICT_LOGP = 9;
    public static final int ERRDICT_HIT = 10;
    public static final int TONE_MATCH = 11;
    public static final int FIRST_CHAR = 12;
    public static final int LEN_DIFF = 13;
    public static final int SPLIT_MERGE = 14;
    public static final int PREFIX_CHANGE = 15;
    /** Edge: smoothed PMI of the two adjacent words. */
    public static final int CTX_PMI = 16;
    /** Edge: the pair was observed at least once. */
    public static final int CTX_SEEN = 17;
    /** Edge: one side is unknown to the context model. */
    public static final int CTX_UNKNOWN = 18;

    public static final int SIZE = 19;

    public static final String[] NAMES = {
            "CHANGE", "CHANNEL", "EDITS", "EDIT_RATIO", "LOG_FREQ", "OOV", "CITY_FREQ", "CITY_SPREAD", "EXPOSE",
            "ERRDICT_LOGP", "ERRDICT_HIT", "TONE_MATCH", "FIRST_CHAR", "LEN_DIFF", "SPLIT_MERGE", "PREFIX_CHANGE",
            "CTX_PMI", "CTX_SEEN", "CTX_UNKNOWN"
    };

    public static int index(String name) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
