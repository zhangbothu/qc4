package com.mapsearch.qc.channel;

/**
 * "Correct but never complete" rule for the token the user is still typing.
 *
 * <p>The typed text is aligned (OSA) against every prefix of the intended word; the best-aligned prefix is the
 * correction, capped at the typed length so that no character beyond what the user typed is added:
 * {@code aple/apple -> appl}, {@code coefe/coffee -> coffe}, {@code lincd/linda -> lind},
 * {@code moaalos/morelos -> morelos}.</p>
 */
public final class PrefixAlign {

    private PrefixAlign() {
    }

    public static String alignedPrefix(String typed, String intended) {
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
        int best = 0;
        for (int i = 1; i <= m; i++) {
            int a = d[i][n];
            int b = d[best][n];
            if (a < b || a == b && Math.abs(i - n) < Math.abs(best - n)
                    || a == b && Math.abs(i - n) == Math.abs(best - n) && i > best) {
                best = i;
            }
        }
        return intended.substring(0, Math.min(best, n));
    }
}
