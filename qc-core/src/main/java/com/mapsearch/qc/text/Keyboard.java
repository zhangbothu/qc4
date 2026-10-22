package com.mapsearch.qc.text;

/**
 * QWERTY geometry (MX / GB / MY / ID mobile keyboards are all QWERTY-based for latin letters).
 * Rows are staggered like a real keyboard so that e.g. 'g'-'b' are neighbours.
 */
public final class Keyboard {

    private static final String[] ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
    private static final double[] ROW_OFFSET = {0.0, 0.25, 0.75};
    private static final double[] X = new double[26];
    private static final double[] Y = new double[26];

    static {
        for (int r = 0; r < ROWS.length; r++) {
            for (int c = 0; c < ROWS[r].length(); c++) {
                int idx = ROWS[r].charAt(c) - 'a';
                X[idx] = c + ROW_OFFSET[r];
                Y[idx] = r;
            }
        }
    }

    private Keyboard() {
    }

    public static double distance(char a, char b) {
        if (a == b) {
            return 0;
        }
        if (a < 'a' || a > 'z' || b < 'a' || b > 'z') {
            return 10;
        }
        double dx = X[a - 'a'] - X[b - 'a'];
        double dy = Y[a - 'a'] - Y[b - 'a'];
        return Math.sqrt(dx * dx + dy * dy);
    }

    public static boolean adjacent(char a, char b) {
        return a != b && distance(a, b) <= 1.3;
    }

    public static boolean isVowel(char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
    }
}
