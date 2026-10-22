package com.mapsearch.qc.lm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-country token bigram statistics over POI names / addresses (and Google top-1 results of online queries).
 * This is the context signal that poi_correcton_v2 lacks: "de moaalos" after "ecatepec" strongly prefers
 * "morelos", "toeer" between "the" and "of" prefers "tower".
 *
 * <p>Scores are smoothed pointwise mutual information so they can be added to unigram evidence without
 * double counting: {@code pmi(a,b) = log P(b|a) - log P(b)} with Dirichlet smoothing
 * {@code P(b|a) = (c(a,b) + mu P(b)) / (c(a,*) + mu)}. Unknown words get 0 (no evidence either way).</p>
 *
 * <p>The vocabulary is independent of the correction lexicon (it also contains short function words such as
 * "de", "of", "jalan" that the lexicon filters out).</p>
 */
public final class BigramModel {

    private static final int FOLLOW_LIMIT = 256;

    private final String[] vocab;
    private final Map<String, Integer> ids;
    private final long[] unigram;
    private final long total;
    private final LongIntMap pairs;
    private final long[] leftTotals;
    private final int[][] nextIds;
    private final int[][] nextCounts;
    private final String[] sortedWords;
    private final long[] sortedPrefixSum;
    private final double mu;

    private BigramModel(String[] vocab, long[] unigram, LongIntMap pairs, double mu) {
        this.vocab = vocab;
        this.unigram = unigram;
        this.pairs = pairs;
        this.mu = mu;
        this.ids = new HashMap<>(vocab.length * 2);
        long t = 0;
        for (int i = 0; i < vocab.length; i++) {
            ids.put(vocab[i], i);
            t += unigram[i];
        }
        this.total = Math.max(1, t);
        this.leftTotals = new long[vocab.length];
        List<List<int[]>> follow = new ArrayList<>(vocab.length);
        for (int i = 0; i < vocab.length; i++) {
            follow.add(null);
        }
        pairs.forEach((key, value) -> {
            int a = (int) (key >>> 32);
            int b = (int) key;
            leftTotals[a] += value;
            List<int[]> l = follow.get(a);
            if (l == null) {
                l = new ArrayList<>();
                follow.set(a, l);
            }
            l.add(new int[]{b, value});
        });
        nextIds = new int[vocab.length][];
        nextCounts = new int[vocab.length][];
        for (int i = 0; i < vocab.length; i++) {
            List<int[]> l = follow.get(i);
            if (l == null) {
                nextIds[i] = new int[0];
                nextCounts[i] = new int[0];
                continue;
            }
            l.sort((x, y) -> Integer.compare(y[1], x[1]));
            int k = Math.min(l.size(), FOLLOW_LIMIT);
            nextIds[i] = new int[k];
            nextCounts[i] = new int[k];
            for (int j = 0; j < k; j++) {
                nextIds[i][j] = l.get(j)[0];
                nextCounts[i][j] = l.get(j)[1];
            }
        }
        Integer[] order = new Integer[vocab.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (x, y) -> vocab[x].compareTo(vocab[y]));
        sortedWords = new String[order.length];
        sortedPrefixSum = new long[order.length + 1];
        for (int i = 0; i < order.length; i++) {
            sortedWords[i] = vocab[order[i]];
            sortedPrefixSum[i + 1] = sortedPrefixSum[i] + unigram[order[i]];
        }
    }

    public static BigramModel of(Map<String, Long> unigrams, Map<String, Map<String, Integer>> bigrams, double mu) {
        Map<String, Integer> id = new HashMap<>();
        List<String> vocab = new ArrayList<>();
        List<Long> uni = new ArrayList<>();
        for (Map.Entry<String, Long> e : unigrams.entrySet()) {
            id.put(e.getKey(), vocab.size());
            vocab.add(e.getKey());
            uni.add(e.getValue());
        }
        LongIntMap pairs = new LongIntMap(1024);
        for (Map.Entry<String, Map<String, Integer>> e : bigrams.entrySet()) {
            Integer a = id.get(e.getKey());
            if (a == null) {
                continue;
            }
            for (Map.Entry<String, Integer> f : e.getValue().entrySet()) {
                Integer b = id.get(f.getKey());
                if (b != null) {
                    pairs.add(((long) a << 32) | (b & 0xffffffffL), f.getValue());
                }
            }
        }
        long[] u = new long[uni.size()];
        for (int i = 0; i < u.length; i++) {
            u[i] = uni.get(i);
        }
        return new BigramModel(vocab.toArray(new String[0]), u, pairs, mu);
    }

    public static BigramModel empty() {
        return of(Map.of(), Map.of(), 20);
    }

    public int vocabSize() {
        return vocab.length;
    }

    public int pairCount() {
        return pairs.size();
    }

    public int id(String w) {
        Integer i = ids.get(w);
        return i == null ? -1 : i;
    }

    public long count(String a, String b) {
        int x = id(a);
        int y = id(b);
        if (x < 0 || y < 0) {
            return 0;
        }
        return pairs.get(((long) x << 32) | (y & 0xffffffffL));
    }

    private double pUni(long c) {
        return (c + 0.5) / (total + 0.5 * vocab.length);
    }

    /** Smoothed PMI of word {@code b} following complete word {@code a}; 0 when either side is unknown. */
    public double pmi(String a, String b) {
        int x = id(a);
        int y = id(b);
        if (x < 0 || y < 0) {
            return 0;
        }
        int c = pairs.get(((long) x << 32) | (y & 0xffffffffL));
        double pb = pUni(unigram[y]);
        double cond = (c + mu * pb) / (leftTotals[x] + mu);
        return Math.log(cond) - Math.log(pb);
    }

    /** PMI of "a followed by any word starting with {@code prefix}" (for the token being typed). */
    public double pmiPrefix(String a, String prefix) {
        int x = id(a);
        if (x < 0) {
            return 0;
        }
        long mass = prefixMass(prefix);
        if (mass == 0) {
            return 0;
        }
        long c = 0;
        int[] nx = nextIds[x];
        int[] nc = nextCounts[x];
        for (int i = 0; i < nx.length; i++) {
            if (vocab[nx[i]].startsWith(prefix)) {
                c += nc[i];
            }
        }
        double pb = Math.min(1.0, (mass + 0.5) / (double) total);
        double cond = (c + mu * pb) / (leftTotals[x] + mu);
        return Math.log(cond) - Math.log(pb);
    }

    public long countPrefix(String a, String prefix) {
        int x = id(a);
        if (x < 0) {
            return 0;
        }
        long c = 0;
        for (int i = 0; i < nextIds[x].length; i++) {
            if (vocab[nextIds[x][i]].startsWith(prefix)) {
                c += nextCounts[x][i];
            }
        }
        return c;
    }

    /** Total unigram count of vocabulary words starting with {@code prefix}. */
    public long prefixMass(String prefix) {
        int lo = lowerBound(prefix);
        int hi = lowerBound(prefix + Character.MAX_VALUE);
        return sortedPrefixSum[hi] - sortedPrefixSum[lo];
    }

    private int lowerBound(String key) {
        int lo = 0;
        int hi = sortedWords.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sortedWords[mid].compareTo(key) < 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    public long unigram(String w) {
        int i = id(w);
        return i < 0 ? 0 : unigram[i];
    }

    String[] vocab() {
        return vocab;
    }

    long[] unigrams() {
        return unigram;
    }

    LongIntMap pairs() {
        return pairs;
    }

    public double mu() {
        return mu;
    }
}
