package com.mapsearch.qc.train;

/**
 * A query with its expected correction. {@code query} keeps a trailing space when the user had finished the
 * last word; otherwise the last token is treated as still being typed.
 */
public record LabeledQuery(int countryId, int cityId, String query, String expected) {

    public boolean needsCorrection() {
        return !query.equals(expected);
    }
}
