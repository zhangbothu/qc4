package com.mapsearch.qc.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryTokenizerTest {

    @Test
    void keepsOffsetsAndTypes() {
        TokenizedQuery q = QueryTokenizer.tokenize("McDonald's  Av. Juárez 12b");
        assertEquals(4, q.size());
        assertEquals("mcdonalds", q.tokens().get(0).norm());
        assertEquals("McDonald's", q.tokens().get(0).raw());
        assertEquals("av", q.tokens().get(1).norm());
        assertEquals(12, q.tokens().get(1).start());
        assertEquals("juarez", q.tokens().get(2).norm());
        assertEquals(16, q.tokens().get(2).start());
        assertEquals(TokenType.MIXED, q.tokens().get(3).type());
        assertFalse(q.lastTokenIncomplete(), "last token is not a word");
    }

    @Test
    void trailingSpaceMeansLastWordFinished() {
        assertTrue(QueryTokenizer.tokenize("starbucks cof").lastTokenIncomplete());
        assertFalse(QueryTokenizer.tokenize("starbucks cof ").lastTokenIncomplete());
        assertTrue(QueryTokenizer.tokenize("Sainsbury'").lastTokenIncomplete());
    }

    @Test
    void nonLatinIsPassedThrough() {
        TokenizedQuery q = QueryTokenizer.tokenize("星巴克 coffee");
        assertEquals(TokenType.OTHER, q.tokens().get(0).type());
        assertEquals(TokenType.WORD, q.tokens().get(1).type());
    }

    @Test
    void casePatternFollowsInput() {
        assertEquals("Morelos", CasePattern.apply("Moaalos", "morelos"));
        assertEquals("BBVA", CasePattern.apply("BBFA", "bbva"));
        assertEquals("tower", CasePattern.apply("toeer", "tower"));
    }
}
