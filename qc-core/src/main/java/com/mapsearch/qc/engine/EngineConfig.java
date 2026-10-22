package com.mapsearch.qc.engine;

import java.util.HashSet;
import java.util.Set;

/** Online knobs. Everything that affects ranking lives in {@link LinearModel}; this is recall / safety. */
public final class EngineConfig {

    /** Empty = every loaded country. */
    public Set<Integer> countryWhitelist = new HashSet<>();
    /** Queries whose normalized length is below this are returned untouched. */
    public int minQueryLength = 4;
    /** Tokens shorter than this are never corrected (they still provide context). */
    public int minTokenLength = 4;
    public int maxTokens = 12;
    public int maxCandidatesPerToken = 10;
    public int trieMaxVisits = 80_000;
    public int maxTrieEdits = 2;

    /** Fast "token is correct" judgement: skips candidate generation for clearly popular tokens. */
    public boolean judgeEnabled = true;
    public long judgeDocFreq = 600;
    public long judgeExactDocFreq = 200;
    public long judgeCityDocFreq = 30;
    public int judgeCityFreq = 15;
    /** Prefix mass (docFreq + weighted exposure of all words under the typed prefix) to trust a typed prefix. */
    public double judgePrefixMass = 3000;

    public boolean phoneticRecall = true;
    public boolean trigramRecall = true;
    public boolean errorDictRecall = true;
    public boolean splitMerge = true;
    public boolean contextModel = true;

    /** Whole-request budget for the engine; the original query is returned (fail-open) when exceeded. */
    public long deadlineMicros = 40_000;

    public EngineConfig copy() {
        EngineConfig c = new EngineConfig();
        c.countryWhitelist = new HashSet<>(countryWhitelist);
        c.minQueryLength = minQueryLength;
        c.minTokenLength = minTokenLength;
        c.maxTokens = maxTokens;
        c.maxCandidatesPerToken = maxCandidatesPerToken;
        c.trieMaxVisits = trieMaxVisits;
        c.maxTrieEdits = maxTrieEdits;
        c.judgeEnabled = judgeEnabled;
        c.judgeDocFreq = judgeDocFreq;
        c.judgeExactDocFreq = judgeExactDocFreq;
        c.judgeCityDocFreq = judgeCityDocFreq;
        c.judgeCityFreq = judgeCityFreq;
        c.judgePrefixMass = judgePrefixMass;
        c.phoneticRecall = phoneticRecall;
        c.trigramRecall = trigramRecall;
        c.errorDictRecall = errorDictRecall;
        c.splitMerge = splitMerge;
        c.contextModel = contextModel;
        c.deadlineMicros = deadlineMicros;
        return c;
    }
}
