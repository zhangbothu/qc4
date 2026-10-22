package com.mapsearch.qc.engine;

/**
 * @param cityId             city of the user / map viewport (from sug's admin ids); 0 when unknown
 * @param lastTokenIncomplete overrides the trailing-space heuristic when sug knows better; null = infer
 */
public record CorrectionRequest(String query, int countryId, int cityId, Boolean lastTokenIncomplete,
                                boolean debug) {

    public static CorrectionRequest of(String query, int countryId, int cityId) {
        return new CorrectionRequest(query, countryId, cityId, null, false);
    }
}
