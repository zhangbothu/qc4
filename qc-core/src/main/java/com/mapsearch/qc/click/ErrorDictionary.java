package com.mapsearch.qc.click;

import com.mapsearch.qc.dict.LexiconIO;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Token-level "error -> correction" table mined from online queries whose Google top-1 POI disambiguates the
 * intended spelling. Used as a recall source and as a strong ranking feature (P(correction | error)).
 * File format: {@code country  error  correction  count  users  prob}.
 */
public final class ErrorDictionary {

    public record Correction(String corrected, long count, int users, double prob) {
    }

    private final Map<Integer, Map<String, List<Correction>>> data;

    public ErrorDictionary(Map<Integer, Map<String, List<Correction>>> data) {
        this.data = data;
    }

    public static ErrorDictionary empty() {
        return new ErrorDictionary(Map.of());
    }

    public List<Correction> lookup(int countryId, String error) {
        Map<String, List<Correction>> m = data.get(countryId);
        if (m == null) {
            return List.of();
        }
        List<Correction> l = m.get(error);
        return l == null ? List.of() : l;
    }

    public int size() {
        return data.values().stream().mapToInt(Map::size).sum();
    }

    public Map<Integer, Map<String, List<Correction>>> asMap() {
        return data;
    }

    public void write(Path file) throws IOException {
        try (BufferedWriter w = LexiconIO.writer(file)) {
            for (Map.Entry<Integer, Map<String, List<Correction>>> c : data.entrySet()) {
                for (Map.Entry<String, List<Correction>> e : c.getValue().entrySet()) {
                    for (Correction k : e.getValue()) {
                        w.write(c.getKey() + "\t" + e.getKey() + "\t" + k.corrected() + "\t" + k.count() + "\t"
                                + k.users() + "\t" + String.format(java.util.Locale.ROOT, "%.4f", k.prob()));
                        w.newLine();
                    }
                }
            }
        }
    }

    public static ErrorDictionary read(Path file) throws IOException {
        Map<Integer, Map<String, List<Correction>>> data = new HashMap<>();
        try (BufferedReader r = LexiconIO.reader(file)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] f = line.split("\t");
                data.computeIfAbsent(Integer.parseInt(f[0]), k -> new HashMap<>())
                        .computeIfAbsent(f[1], k -> new ArrayList<>())
                        .add(new Correction(f[2], Long.parseLong(f[3]), Integer.parseInt(f[4]), Double.parseDouble(f[5])));
            }
        }
        return new ErrorDictionary(data);
    }
}
