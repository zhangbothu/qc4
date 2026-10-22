package com.mapsearch.qc.engine;

import java.util.EnumSet;

/** One hypothesis for the token span {@code [start, end)} of the query. */
public final class Candidate {

    public enum Source { KEEP, TRIE, TRIE_PREFIX, PHONETIC, TRIGRAM, ERROR_DICT, MERGE, SPLIT, FORCED }

    public final int start;
    public final int end;
    /** Normalized output for the span; contains a space for split candidates. */
    public final String output;
    public final boolean keep;
    /** Candidate for the token still being typed: {@link #output} is a prefix, never a completion. */
    public final boolean prefixMode;
    public final EnumSet<Source> sources = EnumSet.noneOf(Source.class);

    String firstWord;
    String lastWord;
    /** Representative lexicon word (the word itself, or the most popular word under the prefix). */
    int lexId = -1;
    int trieNode = -1;
    int edits;
    double channelCost;
    double errDictProb;
    double[] features;
    double nodeScore;
    int index = -1;

    Candidate(int start, int end, String output, boolean keep, boolean prefixMode) {
        this.start = start;
        this.end = end;
        this.output = output;
        this.keep = keep;
        this.prefixMode = prefixMode;
        int sp = output.indexOf(' ');
        this.firstWord = sp < 0 ? output : output.substring(0, sp);
        this.lastWord = sp < 0 ? output : output.substring(output.lastIndexOf(' ') + 1);
    }

    public int edits() {
        return edits;
    }

    public double channelCost() {
        return channelCost;
    }

    public double nodeScore() {
        return nodeScore;
    }

    public double[] features() {
        return features;
    }

    public double errDictProb() {
        return errDictProb;
    }

    public int lexId() {
        return lexId;
    }

    @Override
    public String toString() {
        return "[" + start + "," + end + ")" + output + (keep ? "(keep)" : "") + " s=" + String.format("%.2f", nodeScore);
    }
}
