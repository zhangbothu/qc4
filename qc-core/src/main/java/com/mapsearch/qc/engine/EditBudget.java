package com.mapsearch.qc.engine;

/** Maximum number of edits considered plausible for a token of a given length. */
public final class EditBudget {

    private EditBudget() {
    }

    public static int maxEdits(int len) {
        if (len <= 3) {
            return 0;
        }
        if (len <= 5) {
            return 1;
        }
        if (len <= 10) {
            return 2;
        }
        return 3;
    }
}
