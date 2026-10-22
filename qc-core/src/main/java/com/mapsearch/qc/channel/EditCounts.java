package com.mapsearch.qc.channel;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;

import com.mapsearch.qc.dict.LexiconIO;

/**
 * Character-level edit statistics extracted from (intended, typed) token pairs, e.g. pairs mined from
 * "query vs Google top-1 POI" alignments. Feeds {@link ChannelModel#fromCounts}.
 */
public final class EditCounts {

    static final int A = 27;

    final long[][] sub = new long[A][A];
    final long[] del = new long[A];
    final long[] delDouble = new long[A];
    final long[][] ins = new long[A][A];
    final long[][] trans = new long[A][A];
    final long[] chars = new long[A];
    long pairs;

    static int idx(char c) {
        return c >= 'a' && c <= 'z' ? c - 'a' : 26;
    }

    /**
     * Aligns one pair with unit-cost OSA and records the edit operations.
     *
     * @param prefix when true the typed string is aligned to the best prefix of {@code intended}
     */
    public void addPair(String intended, String typed, boolean prefix, int weight) {
        int m = intended.length();
        int n = typed.length();
        int[][] d = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int v = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1);
                v = Math.min(v, d[i - 1][j - 1] + (intended.charAt(i - 1) == typed.charAt(j - 1) ? 0 : 1));
                if (i > 1 && j > 1 && intended.charAt(i - 1) == typed.charAt(j - 2)
                        && intended.charAt(i - 2) == typed.charAt(j - 1)) {
                    v = Math.min(v, d[i - 2][j - 2] + 1);
                }
                d[i][j] = v;
            }
        }
        int i = m;
        if (prefix) {
            int best = 0;
            for (int k = 0; k <= m; k++) {
                if (d[k][n] < d[best][n] || (d[k][n] == d[best][n] && Math.abs(k - n) < Math.abs(best - n))) {
                    best = k;
                }
            }
            i = best;
        }
        for (int k = 0; k < i; k++) {
            chars[idx(intended.charAt(k))] += weight;
        }
        int j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && intended.charAt(i - 1) == typed.charAt(j - 1) && d[i][j] == d[i - 1][j - 1]) {
                i--;
                j--;
            } else if (i > 1 && j > 1 && intended.charAt(i - 1) == typed.charAt(j - 2)
                    && intended.charAt(i - 2) == typed.charAt(j - 1) && d[i][j] == d[i - 2][j - 2] + 1) {
                trans[idx(intended.charAt(i - 2))][idx(intended.charAt(i - 1))] += weight;
                i -= 2;
                j -= 2;
            } else if (i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + 1) {
                sub[idx(intended.charAt(i - 1))][idx(typed.charAt(j - 1))] += weight;
                i--;
                j--;
            } else if (i > 0 && d[i][j] == d[i - 1][j] + 1) {
                char c = intended.charAt(i - 1);
                if (i > 1 && intended.charAt(i - 2) == c) {
                    delDouble[idx(c)] += weight;
                } else {
                    del[idx(c)] += weight;
                }
                i--;
            } else {
                char prev = j > 1 ? typed.charAt(j - 2) : '^';
                ins[idx(prev)][idx(typed.charAt(j - 1))] += weight;
                j--;
            }
        }
        pairs += weight;
    }

    public long pairs() {
        return pairs;
    }

    public void write(Path file) throws IOException {
        try (BufferedWriter w = LexiconIO.writer(file)) {
            w.write("P\t" + pairs + "\n");
            for (int a = 0; a < A; a++) {
                if (chars[a] > 0) {
                    w.write("C\t" + a + "\t" + chars[a] + "\n");
                }
                if (del[a] > 0) {
                    w.write("D\t" + a + "\t" + del[a] + "\n");
                }
                if (delDouble[a] > 0) {
                    w.write("DD\t" + a + "\t" + delDouble[a] + "\n");
                }
                for (int b = 0; b < A; b++) {
                    if (sub[a][b] > 0) {
                        w.write("S\t" + a + "\t" + b + "\t" + sub[a][b] + "\n");
                    }
                    if (ins[a][b] > 0) {
                        w.write("I\t" + a + "\t" + b + "\t" + ins[a][b] + "\n");
                    }
                    if (trans[a][b] > 0) {
                        w.write("T\t" + a + "\t" + b + "\t" + trans[a][b] + "\n");
                    }
                }
            }
        }
    }

    public static EditCounts read(Path file) throws IOException {
        EditCounts c = new EditCounts();
        try (BufferedReader r = LexiconIO.reader(file)) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t");
                switch (f[0]) {
                    case "P" -> c.pairs = Long.parseLong(f[1]);
                    case "C" -> c.chars[Integer.parseInt(f[1])] = Long.parseLong(f[2]);
                    case "D" -> c.del[Integer.parseInt(f[1])] = Long.parseLong(f[2]);
                    case "DD" -> c.delDouble[Integer.parseInt(f[1])] = Long.parseLong(f[2]);
                    case "S" -> c.sub[Integer.parseInt(f[1])][Integer.parseInt(f[2])] = Long.parseLong(f[3]);
                    case "I" -> c.ins[Integer.parseInt(f[1])][Integer.parseInt(f[2])] = Long.parseLong(f[3]);
                    case "T" -> c.trans[Integer.parseInt(f[1])][Integer.parseInt(f[2])] = Long.parseLong(f[3]);
                    default -> {
                    }
                }
            }
        }
        return c;
    }
}
