package com.mapsearch.qc.corpus;

import com.mapsearch.qc.dict.TokenRecord;
import com.mapsearch.qc.text.Phonetic;
import com.mapsearch.qc.text.TextNormalizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Re-implements the poi_correcton_v2 statistics so that a dictionary can be produced from any POI dump:
 * <ul>
 *   <li>docFreq: number of POIs whose name or address contains the token (counted once per POI)</li>
 *   <li>exposeFreq: number of times the token appears in the top Google result of an online query</li>
 *   <li>cityNum / topCities (top 30, per country)</li>
 *   <li>tone (Double Metaphone), tokenPrefixes</li>
 * </ul>
 * Countries are isolated: the same token in two countries yields two records with different tokenIds.
 */
public final class LexiconBuilder {

    private final int minLen;
    private final int maxLen;
    private final Map<Long, Stat> stats = new HashMap<>();
    private final Map<Integer, Map<String, Long>> ids = new HashMap<>();
    private long nextId = 1;

    private static final class Stat {
        final int countryId;
        final String token;
        long docFreq;
        long expose;
        final Map<Integer, Integer> cities = new HashMap<>();

        Stat(int countryId, String token) {
            this.countryId = countryId;
            this.token = token;
        }
    }

    public LexiconBuilder(int minLen, int maxLen) {
        this.minLen = minLen;
        this.maxLen = maxLen;
    }

    public LexiconBuilder() {
        this(4, 16);
    }

    public void addPoi(PoiRecord poi) {
        if (poi.postcode()) {
            return;
        }
        Set<String> uniq = new LinkedHashSet<>();
        uniq.addAll(TextNormalizer.dictTokens(poi.name()));
        if (poi.address() != null) {
            uniq.addAll(TextNormalizer.dictTokens(poi.address()));
        }
        for (String t : uniq) {
            if (!accept(t)) {
                continue;
            }
            Stat s = stat(poi.countryId(), t);
            s.docFreq++;
            if (poi.cityId() > 0) {
                s.cities.merge(poi.cityId(), 1, Integer::sum);
            }
        }
    }

    /** One online query whose Google top-1 POI had this name / address. */
    public void addExposure(int countryId, String targetName, String targetAddress) {
        Set<String> uniq = new LinkedHashSet<>(TextNormalizer.dictTokens(targetName));
        if (targetAddress != null) {
            uniq.addAll(TextNormalizer.dictTokens(targetAddress));
        }
        for (String t : uniq) {
            if (accept(t)) {
                stat(countryId, t).expose++;
            }
        }
    }

    private boolean accept(String t) {
        return t.length() >= minLen && t.length() <= maxLen && TextNormalizer.isAsciiLetters(t);
    }

    private Stat stat(int countryId, String token) {
        long id = ids.computeIfAbsent(countryId, k -> new HashMap<>()).computeIfAbsent(token, k -> nextId++);
        return stats.computeIfAbsent(id, k -> new Stat(countryId, token));
    }

    public Map<Integer, List<TokenRecord>> build() {
        Map<Integer, List<TokenRecord>> out = new HashMap<>();
        for (Map.Entry<Long, Stat> e : stats.entrySet()) {
            Stat s = e.getValue();
            if (s.docFreq == 0 && s.expose == 0) {
                continue;
            }
            Map<Integer, Integer> top = new HashMap<>();
            s.cities.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(30)
                    .forEach(c -> top.put(c.getKey(), c.getValue()));
            out.computeIfAbsent(s.countryId, k -> new ArrayList<>()).add(new TokenRecord(e.getKey(), s.token,
                    s.countryId, s.docFreq, s.expose, s.cities.size(), top, Phonetic.primary(s.token),
                    prefixes(s.token)));
        }
        return out;
    }

    static List<String> prefixes(String t) {
        int n = t.length();
        if (n <= 4) {
            return List.of();
        }
        if (n == 5) {
            return List.of(t.substring(0, 4));
        }
        List<String> p = new ArrayList<>();
        for (int len = Math.max(3, n - 4); len < n && p.size() < 4; len++) {
            p.add(t.substring(0, len));
        }
        return p;
    }
}
