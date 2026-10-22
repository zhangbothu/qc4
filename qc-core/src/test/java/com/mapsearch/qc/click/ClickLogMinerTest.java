package com.mapsearch.qc.click;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickLogMinerTest {

    @Test
    void alignsTypedPrefixAgainstGoogleTopResult() {
        ClickLogMiner miner = new ClickLogMiner();
        for (int u = 0; u < 3; u++) {
            miner.add(new ClickRecord(276, 0, "u" + u, "roth nyrnber", "Roth Nürnberg", "Nürnberg, Bayern"));
        }
        ErrorDictionary ed = miner.errorDictionary(2, 2, 0.2);
        List<ErrorDictionary.Correction> l = ed.lookup(276, "nyrnber");
        assertEquals(1, l.size());
        assertEquals("nurnber", l.get(0).corrected());
        assertEquals("roth nurnber", miner.samples().get(0).expected());
    }

    @Test
    void unrelatedGoogleResultsAreNotUsedAsLabels() {
        ClickLogMiner miner = new ClickLogMiner();
        miner.add(new ClickRecord(826, 0, "u1", "cheap pizza", "Domino's", "12 High Street, London"));
        assertTrue(miner.samples().isEmpty());
        assertEquals(0, miner.errorDictionary(1, 1, 0.0).size());
    }

    @Test
    void correctQueriesBecomeNegativeSamples() {
        ClickLogMiner miner = new ClickLogMiner();
        miner.add(new ClickRecord(826, 0, "u1", "tower brid", "Tower Bridge", "London SE1 2UP"));
        assertEquals(1, miner.samples().size());
        assertEquals("tower brid", miner.samples().get(0).expected());
    }
}
