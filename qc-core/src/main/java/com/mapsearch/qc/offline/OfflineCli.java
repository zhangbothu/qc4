package com.mapsearch.qc.offline;

import com.mapsearch.qc.channel.ChannelModel;
import com.mapsearch.qc.click.ClickRecord;
import com.mapsearch.qc.click.ErrorDictionary;
import com.mapsearch.qc.corpus.PoiRecord;
import com.mapsearch.qc.dict.LexiconIO;
import com.mapsearch.qc.dict.TokenRecord;
import com.mapsearch.qc.engine.CorrectionRequest;
import com.mapsearch.qc.engine.CorrectionResources;
import com.mapsearch.qc.engine.CorrectionResult;
import com.mapsearch.qc.engine.EngineConfig;
import com.mapsearch.qc.engine.LinearModel;
import com.mapsearch.qc.engine.QueryCorrector;
import com.mapsearch.qc.es.EsClient;
import com.mapsearch.qc.es.EsLexiconLoader;
import com.mapsearch.qc.es.EsPoiCorpusReader;
import com.mapsearch.qc.sample.SampleWorld;
import com.mapsearch.qc.train.Evaluator;
import com.mapsearch.qc.train.LabeledQuery;
import com.mapsearch.qc.train.PerceptronTrainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Offline entry point. {@code java -jar qc-core-*-cli.jar <command> [--key value ...]}
 *
 * <pre>
 *   demo          sample world -> artifacts -> ablation study -> perceptron training -> evaluation
 *   gen-sample    write the synthetic pois / clicks / test TSVs (to try the file-based commands)
 *   build         pois.tsv + clicks.tsv [+ pairs.tsv] [+ lexicon.tsv.gz from ES] -> artifacts dir
 *   train         artifacts dir + labeled.tsv -> model.json
 *   eval          artifacts dir + labeled.tsv -> metrics
 *   correct       artifacts dir + --country --query -> one correction with debug output
 *   es-lexicon    export poi_correcton_v2 (per country) to lexicon.tsv.gz
 *   es-pois       export poi_geo_abroad_v2 names (per country) to pois.tsv for the bigram build
 *   bench         latency with each country's dictionary inflated to production size
 * </pre>
 */
public final class OfflineCli {

    private static final Consumer<String> LOG = s -> System.out.println("  " + s);

    private OfflineCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("commands: demo | gen-sample | build | train | eval | correct | es-lexicon | es-pois"
                    + " | bench");
            return;
        }
        Map<String, String> o = options(args);
        switch (args[0]) {
            case "demo" -> demo(Path.of(o.getOrDefault("out", "data/sample")), o);
            case "gen-sample" -> genSample(Path.of(o.getOrDefault("out", "data/sample-input")));
            case "build" -> build(o);
            case "train" -> train(o);
            case "eval" -> eval(o);
            case "correct" -> correct(o);
            case "es-lexicon" -> esLexicon(o);
            case "es-pois" -> esPois(o);
            case "bench" -> bench(o);
            default -> System.out.println("unknown command " + args[0]);
        }
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String k = args[i].substring(2);
                String v = i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "true";
                m.put(k, v);
            }
        }
        return m;
    }

    static void demo(Path out, Map<String, String> o) throws IOException {
        long t0 = System.currentTimeMillis();
        System.out.println("== 1. sample world (MX / GB / MY / ID)");
        SampleWorld world = Artifacts.Sample.world();
        System.out.println("  POIs: " + world.pois().size());

        System.out.println("== 2. dictionary + context model + click mining");
        Artifacts.Built built = Artifacts.Sample.build(world, LinearModel.defaults(), LOG);
        CorrectionResources full = built.resources();
        List<LabeledQuery> test = world.labeledQueries(Integer.parseInt(o.getOrDefault("test-per-country", "1000")),
                0.55, Artifacts.Sample.TEST_SEED);
        System.out.println("  test queries: " + test.size() + " (needing correction: "
                + test.stream().filter(LabeledQuery::needsCorrection).count() + ")");
        System.out.println("  click-derived training samples: " + built.clickTrainingSamples().size());

        System.out.println("== 3. ablation with hand-set weights");
        EngineConfig noCtx = new EngineConfig();
        noCtx.contextModel = false;
        CorrectionResources noClick = full.withErrorDictionary(ErrorDictionary.empty())
                .withChannel(ChannelModel.defaults());
        report("A  token-only (no context, no click data)", new QueryCorrector(noClick, noCtx), test, 0);
        report("B  + bigram context", new QueryCorrector(noClick, new EngineConfig()), test, 0);
        report("C  + click-mined error dict & channel", new QueryCorrector(full, new EngineConfig()), test, 0);

        System.out.println("== 4. structured perceptron on click-derived labels");
        PerceptronTrainer trainer = new PerceptronTrainer(new EngineConfig(), LOG);
        PerceptronTrainer.Report rep = trainer.train(full, built.clickTrainingSamples(),
                Integer.parseInt(o.getOrDefault("epochs", "6")), 0.05,
                Double.parseDouble(o.getOrDefault("target-precision", "0.92")), 1L);
        CorrectionResources trained = full.withModel(rep.model());
        Evaluator.Metrics m = report("D  + learned weights & calibrated threshold", new QueryCorrector(trained,
                new EngineConfig()), test, 15);
        System.out.print(m.countryBreakdown());
        System.out.println("  sample errors:");
        m.errors.forEach(e -> System.out.println("    " + e));

        Artifacts.write(new Artifacts.Built(trained, built.lexicon(), built.bigrams(), built.miner()), out);
        TsvIO.writeLabeled(out.resolve("test.tsv"), test);
        TsvIO.writeLabeled(out.resolve("train-from-clicks.tsv"), built.clickTrainingSamples());
        System.out.println("== artifacts written to " + out.toAbsolutePath());

        System.out.println("== 5. examples");
        QueryCorrector qc = new QueryCorrector(trained, new EngineConfig());
        examples(qc);
        System.out.printf("done in %.1fs%n", (System.currentTimeMillis() - t0) / 1000.0);
    }

    static void examples(QueryCorrector qc) {
        Object[][] ex = {
                {"aple", SampleWorld.GB, 8260001},
                {"starbucs coefe", SampleWorld.MX, 4840001},
                {"Starbucls coff", SampleWorld.GB, 8260001},
                {"Ecatepec de Moaalos", SampleWorld.MX, 4840002},
                {"The Toeer of London", SampleWorld.GB, 8260001},
                {"petronas twin towrs", SampleWorld.MY, 4580001},
                {"petronas twin towrs ", SampleWorld.MY, 4580001},
                {"masjid istiqal", SampleWorld.ID, 3600001},
                {"masjid istiqal ", SampleWorld.ID, 3600001},
                {"farmacias del ahoro", SampleWorld.MX, 4840001},
                {"coff", SampleWorld.GB, 8260001},
                {"tower bridge", SampleWorld.GB, 8260001},
        };
        for (Object[] e : ex) {
            CorrectionResult r = qc.correct(CorrectionRequest.of((String) e[0], (Integer) e[1], (Integer) e[2]));
            System.out.printf("  [%d] %-24s -> %-24s %s conf=%.2f (%dus)%n", (Integer) e[1], "'" + e[0] + "'",
                    "'" + r.corrected() + "'", r.level(), r.confidence(), r.latencyMicros());
        }
    }

    static Evaluator.Metrics report(String name, QueryCorrector qc, List<LabeledQuery> test, int keepErrors) {
        Evaluator.Metrics m = Evaluator.evaluate(qc, test, keepErrors);
        System.out.println("  " + name);
        System.out.println("     " + m.summary());
        return m;
    }

    /** Same test set, but each country's dictionary inflated with random tokens to production size. */
    static void bench(Map<String, String> o) throws IOException {
        int extra = Integer.parseInt(o.getOrDefault("extra", "300000"));
        SampleWorld world = Artifacts.Sample.world();
        Path modelFile = Path.of(o.getOrDefault("model", "data/sample/model.json"));
        LinearModel model = Files.exists(modelFile) ? LinearModel.load(modelFile) : Artifacts.bundledModel();
        Artifacts.Built built = Artifacts.Sample.build(world, model, s -> { });
        List<LabeledQuery> test = world.labeledQueries(1000, 0.55, Artifacts.Sample.TEST_SEED);
        report("dictionary as built (~1.5k tokens / country)", new QueryCorrector(built.resources(),
                new EngineConfig()), test, 0);

        Map<Integer, List<TokenRecord>> big = new HashMap<>();
        Map<Integer, com.mapsearch.qc.lm.BigramModel> bigrams = new HashMap<>();
        long id = 10_000_000L;
        java.util.Random r = new java.util.Random(5);
        for (Map.Entry<Integer, List<TokenRecord>> e : built.lexicon().entrySet()) {
            List<TokenRecord> l = new ArrayList<>(e.getValue());
            java.util.Set<String> seen = new java.util.HashSet<>();
            l.forEach(t -> seen.add(t.tokenName()));
            for (String w : SampleWorld.fillerVocabulary(e.getKey(), extra, e.getKey())) {
                if (seen.add(w)) {
                    long df = 1 + (long) Math.floor(Math.pow(r.nextDouble(), 6) * 40);
                    l.add(new TokenRecord(id++, w, e.getKey(), df, 0, 1, Map.of(), "", List.of()));
                }
            }
            big.put(e.getKey(), l);
            bigrams.put(e.getKey(), built.resources().country(e.getKey()).bigram());
        }
        long t0 = System.currentTimeMillis();
        CorrectionResources res = Artifacts.assemble(big, bigrams, built.resources().errorDictionary(),
                built.resources().channel(), model, LOG);
        System.out.printf("  inflated dictionaries built in %.1fs%n", (System.currentTimeMillis() - t0) / 1000.0);
        report("dictionary inflated with +" + extra + " random tokens / country", new QueryCorrector(res,
                new EngineConfig()), test, 0);
    }

    static void genSample(Path out) throws IOException {
        SampleWorld world = Artifacts.Sample.world();
        TsvIO.writePois(out.resolve("pois.tsv.gz"), world.pois());
        TsvIO.writeClicks(out.resolve("clicks.tsv.gz"), world.clickLogs(Artifacts.Sample.CLICKS_PER_COUNTRY, 0.55,
                Artifacts.Sample.CLICK_SEED));
        TsvIO.writeLabeled(out.resolve("test.tsv"), world.labeledQueries(1000, 0.55, Artifacts.Sample.TEST_SEED));
        System.out.println("written to " + out.toAbsolutePath());
    }

    static void build(Map<String, String> o) throws IOException {
        Path out = Path.of(o.getOrDefault("out", "data/artifacts"));
        List<PoiRecord> pois = TsvIO.readPois(Path.of(o.get("pois")));
        List<ClickRecord> clicks = o.containsKey("clicks") ? TsvIO.readClicks(Path.of(o.get("clicks"))) : List.of();
        List<TsvIO.MinedPair> pairs = o.containsKey("pairs") ? TsvIO.readPairs(Path.of(o.get("pairs"))) : List.of();
        Artifacts.Built built = Artifacts.build(pois, clicks, pairs, Artifacts.bundledModel(), LOG);
        if (o.containsKey("lexicon")) {
            Map<Integer, List<TokenRecord>> esLex = LexiconIO.read(Path.of(o.get("lexicon")));
            built = new Artifacts.Built(built.resources(), esLex, built.bigrams(), built.miner());
            LOG.accept("using lexicon from " + o.get("lexicon") + " (" + esLex.size() + " countries)");
        }
        Artifacts.write(built, out);
        TsvIO.writeLabeled(out.resolve("train-from-clicks.tsv"), built.clickTrainingSamples());
        System.out.println("artifacts written to " + out.toAbsolutePath());
    }

    static void train(Map<String, String> o) throws IOException {
        Path dir = Path.of(o.getOrDefault("dir", "data/artifacts"));
        CorrectionResources res = Artifacts.load(dir, LOG);
        List<LabeledQuery> data = TsvIO.readLabeled(Path.of(o.getOrDefault("train",
                dir.resolve("train-from-clicks.tsv").toString())));
        PerceptronTrainer.Report rep = new PerceptronTrainer(new EngineConfig(), LOG).train(res.withModel(
                        LinearModel.defaults()), data, Integer.parseInt(o.getOrDefault("epochs", "6")), 0.05,
                Double.parseDouble(o.getOrDefault("target-precision", "0.92")), 1L);
        Path model = Path.of(o.getOrDefault("model", dir.resolve("model.json").toString()));
        rep.model().save(model);
        System.out.println("model written to " + model.toAbsolutePath());
    }

    static void eval(Map<String, String> o) throws IOException {
        Path dir = Path.of(o.getOrDefault("dir", "data/artifacts"));
        CorrectionResources res = Artifacts.load(dir, LOG);
        if (o.containsKey("model")) {
            res = res.withModel(LinearModel.load(Path.of(o.get("model"))));
        }
        List<LabeledQuery> data = TsvIO.readLabeled(Path.of(o.get("test")));
        Evaluator.Metrics m = report("model " + res.model().version(), new QueryCorrector(res, new EngineConfig()),
                data, 30);
        System.out.print(m.countryBreakdown());
        m.errors.forEach(e -> System.out.println("    " + e));
    }

    static void correct(Map<String, String> o) throws IOException {
        Path dir = Path.of(o.getOrDefault("dir", "data/sample"));
        CorrectionResources res = Artifacts.load(dir, s -> { });
        QueryCorrector qc = new QueryCorrector(res, new EngineConfig());
        CorrectionResult r = qc.correct(new CorrectionRequest(o.get("query"), Integer.parseInt(o.get("country")),
                Integer.parseInt(o.getOrDefault("city", "0")), null, true));
        System.out.println(EsClient.json().writerWithDefaultPrettyPrinter().writeValueAsString(r));
    }

    static void esLexicon(Map<String, String> o) throws IOException {
        EsClient es = new EsClient(o.get("es"), o.get("user"), o.get("password"));
        EsLexiconLoader loader = new EsLexiconLoader(es, o.getOrDefault("index", "poi_correcton_v2"),
                EsLexiconLoader.Fields.defaults(), 5000);
        List<TokenRecord> all = new ArrayList<>();
        for (String c : o.get("countries").split(",")) {
            all.addAll(loader.load(Integer.parseInt(c.trim()), LOG));
        }
        Path out = Path.of(o.getOrDefault("out", "data/artifacts/lexicon.tsv.gz"));
        LexiconIO.write(out, all);
        System.out.println(all.size() + " tokens written to " + out.toAbsolutePath());
    }

    static void esPois(Map<String, String> o) throws IOException {
        EsClient es = new EsClient(o.get("es"), o.get("user"), o.get("password"));
        EsPoiCorpusReader reader = new EsPoiCorpusReader(es, o.getOrDefault("index", "poi_geo_abroad_v2"),
                EsPoiCorpusReader.Fields.defaults());
        List<PoiRecord> pois = new ArrayList<>();
        for (String c : o.get("countries").split(",")) {
            reader.read(Integer.parseInt(c.trim()), pois::add, LOG);
        }
        Path out = Path.of(o.getOrDefault("out", "data/input/pois.tsv.gz"));
        Files.createDirectories(out.toAbsolutePath().getParent());
        TsvIO.writePois(out, pois);
        System.out.println(pois.size() + " POI names written to " + out.toAbsolutePath());
    }
}
