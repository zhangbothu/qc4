package com.mapsearch.qc.offline;

import com.mapsearch.qc.click.ClickRecord;
import com.mapsearch.qc.corpus.PoiRecord;
import com.mapsearch.qc.dict.LexiconIO;
import com.mapsearch.qc.train.LabeledQuery;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Plain TSV exchange formats (no quoting; tabs/newlines are stripped from fields):
 * <pre>
 *   pois     poiId  countryId  cityId  name  address  postcode(0|1)
 *   clicks   countryId  cityId  uid  query  googleName  googleAddress
 *   labeled  countryId  cityId  query  expected        (a trailing space in query = last word finished)
 *   pairs    countryId  error  correction  count        (rule-mined pairs, e.g. 276 nyrnber nurnber 1)
 * </pre>
 */
public final class TsvIO {

    private TsvIO() {
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    public static void writePois(Path file, List<PoiRecord> pois) throws IOException {
        try (BufferedWriter w = LexiconIO.writer(file)) {
            for (PoiRecord p : pois) {
                w.write(p.poiId() + "\t" + p.countryId() + "\t" + p.cityId() + "\t" + clean(p.name()) + "\t"
                        + clean(p.address()) + "\t" + (p.postcode() ? 1 : 0) + "\n");
            }
        }
    }

    public static List<PoiRecord> readPois(Path file) throws IOException {
        List<PoiRecord> out = new ArrayList<>();
        for (String[] f : rows(file)) {
            out.add(new PoiRecord(Long.parseLong(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]), f[3],
                    f.length > 4 ? f[4] : "", f.length > 5 && f[5].equals("1")));
        }
        return out;
    }

    public static void writeClicks(Path file, List<ClickRecord> clicks) throws IOException {
        try (BufferedWriter w = LexiconIO.writer(file)) {
            for (ClickRecord c : clicks) {
                w.write(c.countryId() + "\t" + c.cityId() + "\t" + clean(c.uid()) + "\t" + clean(c.query()) + "\t"
                        + clean(c.googleName()) + "\t" + clean(c.googleAddress()) + "\n");
            }
        }
    }

    public static List<ClickRecord> readClicks(Path file) throws IOException {
        List<ClickRecord> out = new ArrayList<>();
        for (String[] f : rows(file)) {
            out.add(new ClickRecord(Integer.parseInt(f[0]), Integer.parseInt(f[1]), f[2], f[3], f[4],
                    f.length > 5 ? f[5] : ""));
        }
        return out;
    }

    public static void writeLabeled(Path file, List<LabeledQuery> data) throws IOException {
        try (BufferedWriter w = LexiconIO.writer(file)) {
            for (LabeledQuery q : data) {
                w.write(q.countryId() + "\t" + q.cityId() + "\t" + clean(q.query()) + "\t" + clean(q.expected()) + "\n");
            }
        }
    }

    public static List<LabeledQuery> readLabeled(Path file) throws IOException {
        List<LabeledQuery> out = new ArrayList<>();
        for (String[] f : rows(file)) {
            out.add(new LabeledQuery(Integer.parseInt(f[0]), Integer.parseInt(f[1]), f[2], f[3]));
        }
        return out;
    }

    /** Output of an existing rule-based pair miner: {@code countryId  error  correction  count}. */
    public record MinedPair(int countryId, String error, String correction, int count) {
    }

    public static List<MinedPair> readPairs(Path file) throws IOException {
        List<MinedPair> out = new ArrayList<>();
        for (String[] f : rows(file)) {
            out.add(new MinedPair(Integer.parseInt(f[0]), f[1], f[2], f.length > 3 ? Integer.parseInt(f[3]) : 1));
        }
        return out;
    }

    private static List<String[]> rows(Path file) throws IOException {
        List<String[]> out = new ArrayList<>();
        try (BufferedReader r = LexiconIO.reader(file)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                out.add(line.split("\t", -1));
            }
        }
        return out;
    }
}
