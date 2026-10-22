package com.mapsearch.qc.click;

/**
 * One online query with the Google Maps top-1 result used as (pseudo) ground truth.
 *
 * @param query         what the user typed (sug input, usually unfinished)
 * @param googleName    name of the Google top-1 POI
 * @param googleAddress address of the Google top-1 POI
 */
public record ClickRecord(int countryId, int cityId, String uid, String query, String googleName,
                          String googleAddress) {
}
