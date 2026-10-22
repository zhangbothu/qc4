package com.mapsearch.qc.dict;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Character-trigram inverted index for long tokens. It recovers candidates that are beyond the edit radius
 * of the trie search (e.g. 3 edits in a 12-letter street name) at the cost of a short postings scan.
 */
public final class TrigramIndex {

    private final Map<Integer, int[]> postings;
    private final String[] words;

    private TrigramIndex(Map<Integer, int[]> postings, String[] words) {
        this.postings = postings;
        this.words = words;
    }

    public static TrigramIndex build(String[] words, int minLen) {
        Map<Integer, List<Integer>> tmp = new HashMap<>();
        for (int id = 0; id < words.length; id++) {
            String w = words[id];
            if (w.length() < minLen) {
                continue;
            }
            for (int g : grams(w)) {
                tmp.computeIfAbsent(g, k -> new ArrayList<>()).add(id);
            }
        }
        Map<Integer, int[]> post = new HashMap<>(tmp.size() * 2);
        tmp.forEach((k, v) -> post.put(k, v.stream().distinct().mapToInt(Integer::intValue).toArray()));
        return new TrigramIndex(post, words);
    }

    static int[] grams(String w) {
        String p = "$" + w + "$";
        int n = p.length() - 2;
        int[] g = new int[n];
        for (int i = 0; i < n; i++) {
            g[i] = (p.charAt(i) << 16) ^ (p.charAt(i + 1) << 8) ^ p.charAt(i + 2);
        }
        return g;
    }

    /** @return word ids whose Dice coefficient with {@code input} is at least {@code minDice}, best first */
    public List<Integer> search(String input, double minDice, int maxPosting, int limit) {
        int[] qg = grams(input);
        Map<Integer, Integer> overlap = new HashMap<>();
        for (int g : qg) {
            int[] p = postings.get(g);
            if (p == null || p.length > maxPosting) {
                continue;
            }
            for (int id : p) {
                overlap.merge(id, 1, Integer::sum);
            }
        }
        List<long[]> scored = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : overlap.entrySet()) {
            int id = e.getKey();
            int gl = words[id].length();
            double dice = 2.0 * e.getValue() / (qg.length + gl);
            if (dice >= minDice) {
                scored.add(new long[]{id, (long) (dice * 1_000_000)});
            }
        }
        scored.sort((a, b) -> Long.compare(b[1], a[1]));
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < scored.size() && i < limit; i++) {
            out.add((int) scored.get(i)[0]);
        }
        return out;
    }
}
