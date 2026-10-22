package com.mapsearch.qc.dict;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuzzySearcherTest {

    private static Lexicon lexicon(String... words) {
        List<TokenRecord> recs = new java.util.ArrayList<>();
        long id = 1;
        for (String w : words) {
            recs.add(new TokenRecord(id++, w, 1, 100 / id, 0, 1, Map.of(), "", List.of()));
        }
        return Lexicon.build(1, recs, 3.0);
    }

    @Test
    void completeModeFindsWordsWithinRadius() {
        Lexicon lex = lexicon("tower", "towers", "power", "town", "london");
        Set<String> hits = new FuzzySearcher().search(lex.trie(), "toeer", 1, false, 10_000).stream()
                .map(FuzzySearcher.Match::path).collect(Collectors.toSet());
        assertEquals(Set.of("tower"), hits);
    }

    @Test
    void prefixModeMatchesWordPrefixes() {
        Lexicon lex = lexicon("coffee", "coffeehouse", "costa", "cofradia");
        Set<String> hits = new FuzzySearcher().search(lex.trie(), "coefe", 1, true, 10_000).stream()
                .map(FuzzySearcher.Match::path).collect(Collectors.toSet());
        assertTrue(hits.contains("coffe"), hits.toString());
    }

    @Test
    void trieAggregatesPopularityUnderPrefixes() {
        Lexicon lex = lexicon("apple", "application", "apply");
        int node = lex.trie().find("appl");
        assertTrue(node >= 0);
        assertEquals("apple", lex.word(lex.trie().bestWord(node)));
        assertTrue(lex.trie().subtreeWeight(node) > lex.weight(lex.id("apple")));
        assertEquals(-1, lex.trie().find("aple"));
    }
}
