package com.mapsearch.qc.lm;

import com.mapsearch.qc.dict.LexiconIO;
import com.mapsearch.qc.text.TextNormalizer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Counts adjacent token pairs per country. Names and addresses are separate sequences (no pair spans the
 * boundary), each POI contributes a pair at most once, and Google top-1 results of online queries can be added
 * with a higher weight because they reflect what users actually search for.
 *
 * <p>File format: {@code U country word count} and {@code B country left right count}.</p>
 */
public final class BigramBuilder {

    private final Map<Integer, Map<String, Long>> unigrams = new HashMap<>();
    private final Map<Integer, Map<String, Map<String, Integer>>> bigrams = new HashMap<>();

    public void addText(int countryId, String text, int weight) {
        if (text == null || text.isBlank()) {
            return;
        }
        List<String> toks = TextNormalizer.dictTokens(text);
        Map<String, Long> uni = unigrams.computeIfAbsent(countryId, k -> new HashMap<>());
        Map<String, Map<String, Integer>> bi = bigrams.computeIfAbsent(countryId, k -> new HashMap<>());
        Set<String> seenTok = new HashSet<>();
        Set<String> seenPair = new HashSet<>();
        for (int i = 0; i < toks.size(); i++) {
            String t = toks.get(i);
            if (seenTok.add(t)) {
                uni.merge(t, (long) weight, Long::sum);
            }
            if (i + 1 < toks.size()) {
                String n = toks.get(i + 1);
                if (seenPair.add(t + ' ' + n)) {
                    bi.computeIfAbsent(t, k -> new HashMap<>()).merge(n, weight, Integer::sum);
                }
            }
        }
    }

    public Map<Integer, BigramModel> build(double mu, int minPairCount) {
        Map<Integer, BigramModel> out = new HashMap<>();
        for (Integer c : unigrams.keySet()) {
            out.put(c, BigramModel.of(unigrams.get(c), prune(bigrams.getOrDefault(c, Map.of()), minPairCount), mu));
        }
        return out;
    }

    private static Map<String, Map<String, Integer>> prune(Map<String, Map<String, Integer>> bi, int min) {
        if (min <= 1) {
            return bi;
        }
        Map<String, Map<String, Integer>> out = new HashMap<>();
        bi.forEach((a, m) -> m.forEach((b, c) -> {
            if (c >= min) {
                out.computeIfAbsent(a, k -> new HashMap<>()).put(b, c);
            }
        }));
        return out;
    }

    public void write(Path file, int minPairCount) throws IOException {
        try (BufferedWriter w = LexiconIO.writer(file)) {
            for (Map.Entry<Integer, Map<String, Long>> e : unigrams.entrySet()) {
                for (Map.Entry<String, Long> u : e.getValue().entrySet()) {
                    w.write("U\t" + e.getKey() + "\t" + u.getKey() + "\t" + u.getValue());
                    w.newLine();
                }
            }
            for (Map.Entry<Integer, Map<String, Map<String, Integer>>> e : bigrams.entrySet()) {
                for (Map.Entry<String, Map<String, Integer>> a : e.getValue().entrySet()) {
                    for (Map.Entry<String, Integer> b : a.getValue().entrySet()) {
                        if (b.getValue() >= minPairCount) {
                            w.write("B\t" + e.getKey() + "\t" + a.getKey() + "\t" + b.getKey() + "\t" + b.getValue());
                            w.newLine();
                        }
                    }
                }
            }
        }
    }

    public static Map<Integer, BigramModel> read(Path file, double mu) throws IOException {
        BigramBuilder b = new BigramBuilder();
        try (BufferedReader r = LexiconIO.reader(file)) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t");
                int country = Integer.parseInt(f[1]);
                if (f[0].equals("U")) {
                    b.unigrams.computeIfAbsent(country, k -> new HashMap<>()).merge(f[2], Long.parseLong(f[3]), Long::sum);
                } else if (f[0].equals("B")) {
                    b.bigrams.computeIfAbsent(country, k -> new HashMap<>())
                            .computeIfAbsent(f[2], k -> new HashMap<>())
                            .merge(f[3], Integer.parseInt(f[4]), Integer::sum);
                }
            }
        }
        return b.build(mu, 1);
    }
}
