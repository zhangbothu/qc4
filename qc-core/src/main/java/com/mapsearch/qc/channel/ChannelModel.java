package com.mapsearch.qc.channel;

import com.mapsearch.qc.text.Keyboard;
import com.mapsearch.qc.text.Phonetic;

import static com.mapsearch.qc.channel.EditCounts.A;
import static com.mapsearch.qc.channel.EditCounts.idx;

/**
 * Error model P(typed | intended) as a weighted Damerau-Levenshtein distance whose edit costs are
 * {@code -ln P(edit)} (Brill & Moore style, single-character edits).
 *
 * <p>Without data the probabilities come from priors (keyboard neighbours, phonetic confusions such as
 * c/k/s/z, b/v, i/y, doubled-letter omissions). With mined click pairs they are re-estimated as
 * {@code (count + kappa * prior) / (charCount + kappa)}, so frequent real confusions become cheap and
 * the prior still covers unseen ones.</p>
 */
public final class ChannelModel {

    private final double[][] sub = new double[A][A];
    private final double[] del = new double[A];
    private final double[] delDouble = new double[A];
    private final double[][] ins = new double[A][A];
    private final double[][] trans = new double[A][A];

    private ChannelModel() {
    }

    private static char ch(int i) {
        return i < 26 ? (char) ('a' + i) : '?';
    }

    static double priorSub(int a, int b) {
        char x = ch(a);
        char y = ch(b);
        if (Phonetic.phoneticPair(x, y)) {
            return 0.03;
        }
        if (Keyboard.adjacent(x, y)) {
            return 0.025;
        }
        if (Keyboard.isVowel(x) && Keyboard.isVowel(y)) {
            return 0.015;
        }
        return 0.002;
    }

    static double priorIns(int prev, int b) {
        if (prev == b) {
            return 0.04;
        }
        if (prev < 26 && Keyboard.adjacent(ch(prev), ch(b))) {
            return 0.012;
        }
        return 0.003;
    }

    public static ChannelModel defaults() {
        return fromCounts(new EditCounts(), 1.0);
    }

    public static ChannelModel fromCounts(EditCounts c, double kappa) {
        ChannelModel m = new ChannelModel();
        for (int a = 0; a < A; a++) {
            double base = c.chars[a] + kappa;
            m.del[a] = -Math.log((c.del[a] + kappa * 0.02) / base);
            m.delDouble[a] = -Math.log((c.delDouble[a] + kappa * 0.08) / base);
            for (int b = 0; b < A; b++) {
                m.sub[a][b] = a == b ? 0 : -Math.log((c.sub[a][b] + kappa * priorSub(a, b)) / base);
                m.ins[a][b] = -Math.log((c.ins[a][b] + kappa * priorIns(a, b)) / base);
                m.trans[a][b] = -Math.log((c.trans[a][b] + kappa * 0.03) / base);
            }
        }
        return m;
    }

    /**
     * @param prefix when true, {@code typed} is compared to the cheapest prefix of {@code intended}
     *               (the user has not finished typing)
     * @return cost in nats (0 for identical strings)
     */
    public double cost(String typed, String intended, boolean prefix) {
        int m = intended.length();
        int n = typed.length();
        double[][] d = new double[m + 1][n + 1];
        for (int j = 1; j <= n; j++) {
            int prev = j > 1 ? idx(typed.charAt(j - 2)) : 26;
            d[0][j] = d[0][j - 1] + ins[prev][idx(typed.charAt(j - 1))];
        }
        for (int i = 1; i <= m; i++) {
            char ci = intended.charAt(i - 1);
            int a = idx(ci);
            boolean doubled = i > 1 && intended.charAt(i - 2) == ci;
            double delCost = doubled ? delDouble[a] : del[a];
            d[i][0] = d[i - 1][0] + delCost;
            for (int j = 1; j <= n; j++) {
                char tj = typed.charAt(j - 1);
                int prevTyped = j > 1 ? idx(typed.charAt(j - 2)) : 26;
                double v = d[i - 1][j] + delCost;
                v = Math.min(v, d[i][j - 1] + ins[prevTyped][idx(tj)]);
                v = Math.min(v, d[i - 1][j - 1] + (ci == tj ? 0 : sub[a][idx(tj)]));
                if (i > 1 && j > 1 && ci == typed.charAt(j - 2) && intended.charAt(i - 2) == tj) {
                    v = Math.min(v, d[i - 2][j - 2] + trans[idx(intended.charAt(i - 2))][a]);
                }
                d[i][j] = v;
            }
        }
        if (!prefix) {
            return d[m][n];
        }
        double best = Double.MAX_VALUE;
        for (int i = 0; i <= m; i++) {
            best = Math.min(best, d[i][n]);
        }
        return best;
    }

    /** Unit-cost OSA distance (prefix-aware), used for edit radius checks and features. */
    public static int osa(String typed, String intended, boolean prefix) {
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
        if (!prefix) {
            return d[m][n];
        }
        int best = Integer.MAX_VALUE;
        for (int i = 0; i <= m; i++) {
            best = Math.min(best, d[i][n]);
        }
        return best;
    }
}
