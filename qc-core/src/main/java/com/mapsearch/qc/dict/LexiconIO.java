package com.mapsearch.qc.dict;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * TSV(.gz) snapshot of poi_correcton_v2 for one or more countries:
 * {@code tokenId  tokenName  countryId  docFreq  exposeFreq  cityNum  city:freq,city:freq  tone  p1|p2}
 */
public final class LexiconIO {

    private LexiconIO() {
    }

    public static void write(Path file, List<TokenRecord> records) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (BufferedWriter w = writer(file)) {
            for (TokenRecord r : records) {
                String cities = r.topCities() == null ? "" : r.topCities().entrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                        .map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(","));
                String prefixes = r.tokenPrefixes() == null ? "" : String.join("|", r.tokenPrefixes());
                w.write(r.tokenId() + "\t" + r.tokenName() + "\t" + r.countryId() + "\t" + r.totalDocFreq() + "\t"
                        + r.totalExposeFreq() + "\t" + r.totalCityNum() + "\t" + cities + "\t"
                        + (r.tone() == null ? "" : r.tone()) + "\t" + prefixes);
                w.newLine();
            }
        }
    }

    public static Map<Integer, List<TokenRecord>> read(Path file) throws IOException {
        Map<Integer, List<TokenRecord>> byCountry = new HashMap<>();
        try (BufferedReader r = reader(file)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split("\t", -1);
                Map<Integer, Integer> cities = new HashMap<>();
                if (f.length > 6 && !f[6].isEmpty()) {
                    for (String kv : f[6].split(",")) {
                        int k = kv.indexOf(':');
                        cities.put(Integer.parseInt(kv.substring(0, k)), Integer.parseInt(kv.substring(k + 1)));
                    }
                }
                List<String> prefixes = f.length > 8 && !f[8].isEmpty() ? Arrays.asList(f[8].split("\\|")) : List.of();
                TokenRecord rec = new TokenRecord(Long.parseLong(f[0]), f[1], Integer.parseInt(f[2]),
                        Long.parseLong(f[3]), Long.parseLong(f[4]), Integer.parseInt(f[5]), cities,
                        f.length > 7 ? f[7] : "", prefixes);
                byCountry.computeIfAbsent(rec.countryId(), k -> new ArrayList<>()).add(rec);
            }
        }
        return byCountry;
    }

    public static BufferedWriter writer(Path file) throws IOException {
        if (file.toAbsolutePath().getParent() != null) {
            Files.createDirectories(file.toAbsolutePath().getParent());
        }
        if (file.toString().endsWith(".gz")) {
            return new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file)),
                    StandardCharsets.UTF_8));
        }
        return Files.newBufferedWriter(file, StandardCharsets.UTF_8);
    }

    public static BufferedReader reader(Path file) throws IOException {
        if (file.toString().endsWith(".gz")) {
            return new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)),
                    StandardCharsets.UTF_8));
        }
        return Files.newBufferedReader(file, StandardCharsets.UTF_8);
    }
}
