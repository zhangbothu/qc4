package com.mapsearch.qc.engine;

import com.mapsearch.qc.text.TokenizedQuery;

/** Narrow access to engine internals for offline training / evaluation. */
public final class TrainingHooks {

    private TrainingHooks() {
    }

    public static Lattice lattice(QueryCorrector qc, TokenizedQuery tq, CountryResources cr, int cityId) {
        return qc.builder().build(tq, cr, cityId, Long.MAX_VALUE);
    }

    public static double[] margins(Lattice lat, LatticeDecoder.Result r, Candidate c) {
        return QueryCorrector.confidence(lat, r, c);
    }
}
