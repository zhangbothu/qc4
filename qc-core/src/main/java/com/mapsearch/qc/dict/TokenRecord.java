package com.mapsearch.qc.dict;

import java.util.List;
import java.util.Map;

/**
 * One document of the {@code poi_correcton_v2} index (one token in one country's dictionary).
 *
 * @param topCities cityId -> number of POIs in that city containing the token (top 30 only)
 * @param tone      Double Metaphone code; recomputed when empty
 */
public record TokenRecord(long tokenId,
                          String tokenName,
                          int countryId,
                          long totalDocFreq,
                          long totalExposeFreq,
                          int totalCityNum,
                          Map<Integer, Integer> topCities,
                          String tone,
                          List<String> tokenPrefixes) {
}
