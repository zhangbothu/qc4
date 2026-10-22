package com.mapsearch.qc.text;

public enum TokenType {
    /** Latin letters only after folding; the only type eligible for correction. */
    WORD,
    NUMERIC,
    /** Letters mixed with digits, e.g. "a12", "7eleven" (house numbers, postcodes). */
    MIXED,
    /** CJK, Thai, Arabic, ... — passed through untouched. */
    OTHER
}
