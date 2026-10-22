package com.mapsearch.qc.corpus;

/**
 * The subset of a poi_geo_abroad_v2 document needed offline.
 *
 * @param postcode true for postcode-type POIs, which are excluded from the token statistics
 */
public record PoiRecord(long poiId, int countryId, int cityId, String name, String address, boolean postcode) {
}
