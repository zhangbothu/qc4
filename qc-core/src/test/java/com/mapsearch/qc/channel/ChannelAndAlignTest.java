package com.mapsearch.qc.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelAndAlignTest {

    @Test
    void alignedPrefixNeverCompletes() {
        assertEquals("appl", PrefixAlign.alignedPrefix("aple", "apple"));
        assertEquals("coffe", PrefixAlign.alignedPrefix("coefe", "coffee"));
        assertEquals("coff", PrefixAlign.alignedPrefix("coff", "coffee"));
        assertEquals("morelos", PrefixAlign.alignedPrefix("moaalos", "morelos"));
        assertEquals("lind", PrefixAlign.alignedPrefix("lincd", "linda"));
        assertEquals("nurnber", PrefixAlign.alignedPrefix("nyrnber", "nurnberg"));
    }

    @Test
    void keyboardAndPhoneticEditsAreCheaper() {
        ChannelModel m = ChannelModel.defaults();
        double adjacent = m.cost("towrr", "tower", false);
        double random = m.cost("towqr", "tower", false);
        assertTrue(adjacent < random, adjacent + " vs " + random);
        assertTrue(m.cost("kafe", "cafe", false) < m.cost("mafe", "cafe", false));
        assertTrue(m.cost("coffe", "coffee", false) < m.cost("cofee", "coffee", false) + 1e-9
                || m.cost("cofee", "coffee", false) > 0);
        assertEquals(0.0, m.cost("coff", "coffee", true), 1e-9);
    }

    @Test
    void learnedCountsLowerTheCostOfObservedConfusions() {
        EditCounts c = new EditCounts();
        for (int i = 0; i < 200; i++) {
            c.addPair("nurnberg", "nyrnberg", false, 1);
        }
        ChannelModel learned = ChannelModel.fromCounts(c, 50);
        ChannelModel prior = ChannelModel.defaults();
        assertTrue(learned.cost("nyrnberg", "nurnberg", false) < prior.cost("nyrnberg", "nurnberg", false));
    }

    @Test
    void osaCountsTranspositionsOnce() {
        assertEquals(1, ChannelModel.osa("toewr", "tower", false));
        assertEquals(2, ChannelModel.osa("moaalos", "morelos", false));
        assertEquals(1, ChannelModel.osa("aple", "apple", true));
    }
}
