package com.mapsearch.qc.offline;

import com.mapsearch.qc.channel.ChannelModel;
import com.mapsearch.qc.channel.EditCounts;
import com.mapsearch.qc.click.ClickLogMiner;
import com.mapsearch.qc.click.ClickRecord;
import com.mapsearch.qc.click.ErrorDictionary;
import com.mapsearch.qc.corpus.LexiconBuilder;
import com.mapsearch.qc.corpus.PoiRecord;
import com.mapsearch.qc.dict.Lexicon;
import com.mapsearch.qc.dict.LexiconIO;
import com.mapsearch.qc.dict.TokenRecord;
import com.mapsearch.qc.engine.CorrectionResources;
import com.mapsearch.qc.engine.CountryResources;
import com.mapsearch.qc.engine.LinearModel;
import com.mapsearch.qc.lm.BigramBuilder;
import com.mapsearch.qc.lm.BigramModel;
import com.mapsearch.qc.sample.SampleWorld;
import com.mapsearch.qc.train.LabeledQuery;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builds / persists / loads the artifact set consumed by the online engine:
 * <pre>
 *   lexicon.tsv.gz   snapshot of poi_correcton_v2 (or rebuilt from a POI dump)
 *   bigram.tsv.gz    context model (POI names / addresses + Google top-1 results)
 *   errdict.tsv      click-mined error dictionary
 *   channel.tsv      click-mined character edit statistics
 *   model.json       ranker weights + calibration + thresholds
 * </pre>
 */
public final class Artifacts {

    public static final double EXPOSE_WEIGHT = 3.0;
    public static final double BIGRAM_MU = 20.0;
    public static final double CHANNEL_KAPPA = 50.0;
    public static final String BUNDLED_MODEL = "/models/sample-model.json";

    public record Built(CorrectionResources resources, Map<Integer, List<TokenRecord>> lexicon,
                       BigramBuilder bigrams, ClickLogMiner miner) {
        public List<LabeledQuery> clickTrainingSamples() {
            return miner.samples();
        }
    }

    private Artifacts() {
    }

    /** Full offline build from a POI corpus + click logs. */
    public static Built build(List<PoiRecord> pois, List<ClickRecord> clicks, LinearModel model, Consumer<String> log) {
        return build(pois, clicks, List.of(), model, log);
    }

    /**
     * {@code pairs} come from a pre-existing rule miner without user ids; each occurrence is counted as a distinct
     * pseudo-user so a pair needs at least two occurrences to pass the same support filter as click pairs.
     */
    public static Built build(List<PoiRecord> pois, List<ClickRecord> clicks, List<TsvIO.MinedPair> pairs,
                              LinearModel model, Consumer<String> log) {
        LexiconBuilder lb = new LexiconBuilder();
        BigramBuilder bb = new BigramBuilder();
        for (PoiRecord p : pois) {
            lb.addPoi(p);
            if (!p.postcode()) {
                bb.addText(p.countryId(), p.name(), 1);
                bb.addText(p.countryId(), p.address(), 1);
            }
        }
        ClickLogMiner miner = new ClickLogMiner();
        for (ClickRecord c : clicks) {
            lb.addExposure(c.countryId(), c.googleName(), c.googleAddress());
            bb.addText(c.countryId(), c.googleName(), 2);
            miner.add(c);
        }
        log.accept(miner.summary());
        int pairNo = 0;
        for (TsvIO.MinedPair p : pairs) {
            pairNo++;
            for (int k = 0; k < p.count(); k++) {
                miner.addMinedPair(p.countryId(), "pair" + pairNo + "#" + k, p.error(), p.correction(), false);
            }
        }
        if (!pairs.isEmpty()) {
            log.accept("rule-mined pairs added: " + pairs.size());
        }
        Map<Integer, List<TokenRecord>> lex = lb.build();
        Map<Integer, BigramModel> bigrams = bb.build(BIGRAM_MU, 1);
        ErrorDictionary ed = miner.errorDictionary(2, 2, 0.2);
        log.accept("error dictionary entries: " + ed.size());
        ChannelModel channel = ChannelModel.fromCounts(miner.editCounts(), CHANNEL_KAPPA);
        return new Built(assemble(lex, bigrams, ed, channel, model, log), lex, bb, miner);
    }

    public static CorrectionResources assemble(Map<Integer, List<TokenRecord>> lex, Map<Integer, BigramModel> bigrams,
                                               ErrorDictionary ed, ChannelModel channel, LinearModel model,
                                               Consumer<String> log) {
        Map<Integer, CountryResources> countries = new HashMap<>();
        for (Map.Entry<Integer, List<TokenRecord>> e : lex.entrySet()) {
            Lexicon l = Lexicon.build(e.getKey(), e.getValue(), EXPOSE_WEIGHT);
            BigramModel bg = bigrams.getOrDefault(e.getKey(), BigramModel.empty());
            countries.put(e.getKey(), new CountryResources(e.getKey(), l, bg));
            log.accept(String.format("country %d: %d tokens, trie %d nodes (~%d KB), bigram vocab %d, pairs %d",
                    e.getKey(), l.size(), l.trie().size(), l.trie().memoryBytes() / 1024, bg.vocabSize(),
                    bg.pairCount()));
        }
        return new CorrectionResources(countries, channel, ed, model);
    }

    public static void write(Built b, Path dir) throws IOException {
        Files.createDirectories(dir);
        LexiconIO.write(dir.resolve("lexicon.tsv.gz"),
                b.lexicon().values().stream().flatMap(List::stream).toList());
        b.bigrams().write(dir.resolve("bigram.tsv.gz"), 1);
        b.resources().errorDictionary().write(dir.resolve("errdict.tsv"));
        b.miner().editCounts().write(dir.resolve("channel.tsv"));
        b.resources().model().save(dir.resolve("model.json"));
    }

    /** Loads artifacts written by {@link #write} (or produced by the production offline jobs). */
    public static CorrectionResources load(Path dir, Consumer<String> log) throws IOException {
        Map<Integer, List<TokenRecord>> lex = LexiconIO.read(dir.resolve("lexicon.tsv.gz"));
        Map<Integer, BigramModel> bigrams = Files.exists(dir.resolve("bigram.tsv.gz"))
                ? BigramBuilder.read(dir.resolve("bigram.tsv.gz"), BIGRAM_MU) : Map.of();
        ErrorDictionary ed = Files.exists(dir.resolve("errdict.tsv"))
                ? ErrorDictionary.read(dir.resolve("errdict.tsv")) : ErrorDictionary.empty();
        ChannelModel channel = Files.exists(dir.resolve("channel.tsv"))
                ? ChannelModel.fromCounts(EditCounts.read(dir.resolve("channel.tsv")), CHANNEL_KAPPA)
                : ChannelModel.defaults();
        LinearModel model = Files.exists(dir.resolve("model.json"))
                ? LinearModel.load(dir.resolve("model.json")) : bundledModel();
        return assemble(lex, bigrams, ed, channel, model, log);
    }

    public static LinearModel bundledModel() {
        try (InputStream in = Artifacts.class.getResourceAsStream(BUNDLED_MODEL)) {
            return in == null ? LinearModel.defaults() : LinearModel.load(in);
        } catch (IOException e) {
            return LinearModel.defaults();
        }
    }

    /** Sample world used by the demo service and tests (deterministic). */
    public static final class Sample {
        public static final int POIS_PER_COUNTRY = 12_000;
        public static final int CLICKS_PER_COUNTRY = 10_000;
        public static final long WORLD_SEED = 20261023L;
        public static final long CLICK_SEED = 7L;
        public static final long TEST_SEED = 99L;

        private Sample() {
        }

        public static SampleWorld world() {
            return SampleWorld.generate(POIS_PER_COUNTRY, WORLD_SEED);
        }

        public static Built build(SampleWorld world, LinearModel model, Consumer<String> log) {
            return Artifacts.build(world.pois(), world.clickLogs(CLICKS_PER_COUNTRY, 0.55, CLICK_SEED), model, log);
        }
    }
}
