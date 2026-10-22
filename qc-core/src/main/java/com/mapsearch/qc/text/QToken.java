package com.mapsearch.qc.text;

/**
 * @param raw   surface form exactly as typed
 * @param norm  folded dictionary key (only meaningful for {@link TokenType#WORD})
 * @param start offset of the raw token in the original query
 * @param end   exclusive end offset
 */
public record QToken(String raw, String norm, int start, int end, TokenType type) {
}
