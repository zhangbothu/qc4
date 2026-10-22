package com.mapsearch.qc.engine;

import java.util.List;
import java.util.Map;

public record CorrectionResult(String original,
                               String corrected,
                               boolean changed,
                               double confidence,
                               Level level,
                               List<TokenCorrection> corrections,
                               String skipReason,
                               long latencyMicros,
                               List<TokenDebug> debug) {

    public enum Level { NONE, LOW, MEDIUM, HIGH }

    /**
     * @param applied false when the decoder proposed the change but confidence was below the MEDIUM threshold
     */
    public record TokenCorrection(int tokenStart, int tokenEnd, String from, String to, double confidence,
                                  Level level, boolean applied, int edits, List<String> sources) {
    }

    public record CandidateDebug(String output, boolean keep, List<String> sources, int edits, double channelCost,
                                 double nodeScore, double maxMarginal, boolean chosen, Map<String, Double> features) {
    }

    public record TokenDebug(int index, String raw, String norm, boolean prefixMode, String status,
                             List<CandidateDebug> candidates) {
    }
}
