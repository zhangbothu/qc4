package com.mapsearch.qc.dict;

import com.mapsearch.qc.text.Phonetic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable in-memory snapshot of one country's slice of {@code poi_correcton_v2}.
 * Online correction never calls ES on the hot path: the whole dictionary is loaded at start-up / refresh
 * and swapped atomically, which is what makes a ~60ms end-to-end budget comfortable.
 */
public final class Lexicon {

    private final int countryId;
    private final String[] words;
    private final long[] tokenIds;
    private final long[] docFreq;
    private final long[] exposeFreq;
    private final int[] cityNum;
    private final int[][] cityIds;
    private final int[][] cityFreqs;
    private final String[] tone;
    private final double[] weight;
    private final double totalWeight;
    private final Map<String, Integer> index;
    private final CompactTrie trie;
    private final Map<String, int[]> byTone;
    private final TrigramIndex trigrams;

    private Lexicon(int countryId, List<TokenRecord> records, double exposeWeight) {
        this.countryId = countryId;
        int n = records.size();
        words = new String[n];
        tokenIds = new long[n];
        docFreq = new long[n];
        exposeFreq = new long[n];
        cityNum = new int[n];
        cityIds = new int[n][];
        cityFreqs = new int[n][];
        tone = new String[n];
        weight = new double[n];
        index = new HashMap<>(n * 2);
        double total = 0;
        for (int i = 0; i < n; i++) {
            TokenRecord r = records.get(i);
            words[i] = r.tokenName();
            tokenIds[i] = r.tokenId();
            docFreq[i] = r.totalDocFreq();
            exposeFreq[i] = r.totalExposeFreq();
            cityNum[i] = r.totalCityNum();
            Map<Integer, Integer> tc = r.topCities() == null ? Map.of() : r.topCities();
            int[] ids = tc.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
            int[] fs = new int[ids.length];
            for (int k = 0; k < ids.length; k++) {
                fs[k] = tc.get(ids[k]);
            }
            cityIds[i] = ids;
            cityFreqs[i] = fs;
            tone[i] = r.tone() == null || r.tone().isEmpty() ? Phonetic.primary(r.tokenName()) : r.tone();
            weight[i] = r.totalDocFreq() + exposeWeight * r.totalExposeFreq() + 1.0;
            total += weight[i];
            index.put(words[i], i);
        }
        totalWeight = total;
        trie = CompactTrie.build(words, weight);
        byTone = buildToneIndex();
        trigrams = TrigramIndex.build(words, 6);
    }

    /**
     * @param exposeWeight how much one online exposure is worth relative to one POI occurrence.
     *                     Exposure reflects what users actually look for, so it is usually weighted higher.
     */
    public static Lexicon build(int countryId, Collection<TokenRecord> records, double exposeWeight) {
        List<TokenRecord> clean = new ArrayList<>(records.size());
        Map<String, Integer> seen = new HashMap<>();
        for (TokenRecord r : records) {
            if (r.tokenName() == null || r.tokenName().isEmpty() || !isAz(r.tokenName())) {
                continue;
            }
            Integer prev = seen.putIfAbsent(r.tokenName(), clean.size());
            if (prev == null) {
                clean.add(r);
            }
        }
        return new Lexicon(countryId, clean, exposeWeight);
    }

    private static boolean isAz(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 'a' || c > 'z') {
                return false;
            }
        }
        return true;
    }

    private Map<String, int[]> buildToneIndex() {
        Map<String, List<Integer>> tmp = new HashMap<>();
        for (int i = 0; i < words.length; i++) {
            if (!tone[i].isEmpty()) {
                tmp.computeIfAbsent(tone[i], k -> new ArrayList<>()).add(i);
            }
        }
        Map<String, int[]> res = new HashMap<>(tmp.size() * 2);
        for (Map.Entry<String, List<Integer>> e : tmp.entrySet()) {
            int[] ids = e.getValue().stream().mapToInt(Integer::intValue).toArray();
            Integer[] boxed = Arrays.stream(ids).boxed().toArray(Integer[]::new);
            Arrays.sort(boxed, (a, b) -> Double.compare(weight[b], weight[a]));
            int keep = Math.min(boxed.length, 64);
            int[] top = new int[keep];
            for (int k = 0; k < keep; k++) {
                top[k] = boxed[k];
            }
            res.put(e.getKey(), top);
        }
        return res;
    }

    public int countryId() {
        return countryId;
    }

    public int size() {
        return words.length;
    }

    public int id(String word) {
        Integer id = index.get(word);
        return id == null ? -1 : id;
    }

    public String word(int id) {
        return words[id];
    }

    public long tokenId(int id) {
        return tokenIds[id];
    }

    public long docFreq(int id) {
        return docFreq[id];
    }

    public long exposeFreq(int id) {
        return exposeFreq[id];
    }

    public int cityNum(int id) {
        return cityNum[id];
    }

    public String tone(int id) {
        return tone[id];
    }

    public double weight(int id) {
        return weight[id];
    }

    public double totalWeight() {
        return totalWeight;
    }

    public int cityFreq(int id, int cityId) {
        if (cityId <= 0) {
            return 0;
        }
        int k = Arrays.binarySearch(cityIds[id], cityId);
        return k >= 0 ? cityFreqs[id][k] : 0;
    }

    public CompactTrie trie() {
        return trie;
    }

    public int[] byTone(String code) {
        int[] ids = byTone.get(code);
        return ids == null ? new int[0] : ids;
    }

    public TrigramIndex trigrams() {
        return trigrams;
    }

    public List<TokenRecord> records() {
        List<TokenRecord> out = new ArrayList<>(words.length);
        for (int i = 0; i < words.length; i++) {
            Map<Integer, Integer> tc = new HashMap<>();
            for (int k = 0; k < cityIds[i].length; k++) {
                tc.put(cityIds[i][k], cityFreqs[i][k]);
            }
            out.add(new TokenRecord(tokenIds[i], words[i], countryId, docFreq[i], exposeFreq[i], cityNum[i], tc,
                    tone[i], List.of()));
        }
        return out;
    }
}
