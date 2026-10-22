package com.mapsearch.qc.engine;

import com.mapsearch.qc.offline.Artifacts;
import com.mapsearch.qc.sample.SampleWorld;
import com.mapsearch.qc.train.Evaluator;
import com.mapsearch.qc.train.LabeledQuery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndToEndExamplesTest {

    private static SampleWorld world;
    private static QueryCorrector qc;

    @BeforeAll
    static void setUp() {
        world = Artifacts.Sample.world();
        CorrectionResources res = Artifacts.Sample.build(world, Artifacts.bundledModel(), s -> { }).resources();
        qc = new QueryCorrector(res, new EngineConfig());
    }

    private static CorrectionResult run(String q, int country, int city) {
        return qc.correct(new CorrectionRequest(q, country, city, null, true));
    }

    @ParameterizedTest(name = "[{1}] ''{0}'' -> ''{3}''")
    @CsvSource(delimiter = '|', value = {
            "aple|826|8260001|appl",
            "starbucs coefe|484|4840001|starbucks coffe",
            "Starbucls coff|826|8260001|Starbucks coff",
            "Ecatepec de Moaalos|484|4840002|Ecatepec de Morelos",
            "The Toeer of London|826|8260001|The Tower of London",
            "petronas twin towrs|458|4580001|petronas twin tower",
            "masjid istiqal|360|3600001|masjid istiqla",
    })
    void userExamples(String query, int country, int city, String expected) {
        assertEquals(expected, run(query, country, city).corrected());
    }

    @Test
    void finishedWordsAreCorrectedInFull() {
        assertEquals("petronas twin towers ", run("petronas twin towrs ", 458, 4580001).corrected());
    }

    @Test
    void legalPrefixesAndCorrectQueriesAreLeftAlone() {
        for (String q : new String[]{"coff", "starbucks cof", "tower bridge", "Ecatepec de Mor", "the tower of l"}) {
            CorrectionResult r = run(q, 826, 8260001);
            if (q.startsWith("Ecatepec")) {
                r = run(q, 484, 4840002);
            }
            assertFalse(r.changed(), q + " -> " + r.corrected());
        }
    }

    @Test
    void punctuationAndCaseArePreserved() {
        CorrectionResult r = run("THE TOEER OF LONDON, UK ", 826, 8260001);
        assertEquals("THE TOWER OF LONDON, UK ", r.corrected());
    }

    @Test
    void unknownCountryFailsOpen() {
        CorrectionResult r = run("aple", 999, 0);
        assertFalse(r.changed());
        assertEquals("country_not_enabled", r.skipReason());
    }

    @Test
    void debugExposesTheLattice() {
        CorrectionResult r = run("Ecatepec de Moaalos", 484, 4840002);
        assertEquals(3, r.debug().size());
        assertTrue(r.debug().get(2).prefixMode());
        assertTrue(r.debug().get(2).candidates().stream().anyMatch(c -> c.chosen() && c.output().equals("morelos")));
    }

    @Test
    void heldOutQualityAndLatency() {
        List<LabeledQuery> test = world.labeledQueries(250, 0.55, Artifacts.Sample.TEST_SEED);
        Evaluator.Metrics m = Evaluator.evaluate(qc, test, 0);
        assertTrue(m.precision() > 0.9, m.summary());
        assertTrue(m.falseRate() < 0.03, m.summary());
        assertTrue(m.latencyPercentile(0.99) < 20_000, m.summary());
    }

    @Test
    void modelRoundTrips() throws Exception {
        Path tmp = Files.createTempFile("model", ".json");
        LinearModel m = qc.resources().model();
        m.save(tmp);
        LinearModel back = LinearModel.load(tmp);
        assertEquals(m.weight(Features.CTX_PMI), back.weight(Features.CTX_PMI), 1e-4);
        assertEquals(m.mediumThreshold(), back.mediumThreshold(), 1e-9);
    }
}
