package com.mapsearch.qc.service;

import com.mapsearch.qc.channel.ChannelModel;
import com.mapsearch.qc.channel.EditCounts;
import com.mapsearch.qc.click.ErrorDictionary;
import com.mapsearch.qc.dict.TokenRecord;
import com.mapsearch.qc.engine.CorrectionResources;
import com.mapsearch.qc.engine.EngineConfig;
import com.mapsearch.qc.engine.LinearModel;
import com.mapsearch.qc.engine.QueryCorrector;
import com.mapsearch.qc.es.EsClient;
import com.mapsearch.qc.es.EsLexiconLoader;
import com.mapsearch.qc.lm.BigramBuilder;
import com.mapsearch.qc.lm.BigramModel;
import com.mapsearch.qc.offline.Artifacts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the current {@link QueryCorrector}. Reloads build a complete new snapshot off the request path and swap
 * it in atomically, so requests never see a half-loaded dictionary.
 */
@Component
public class CorrectorHolder {

    private static final Logger log = LoggerFactory.getLogger(CorrectorHolder.class);

    private final QcProperties props;
    private final AtomicReference<QueryCorrector> current = new AtomicReference<>();
    private volatile long loadedAt;
    private volatile long loadMillis;

    public CorrectorHolder(QcProperties props) throws IOException {
        this.props = props;
        reload();
    }

    public QueryCorrector get() {
        return current.get();
    }

    public long loadedAt() {
        return loadedAt;
    }

    public long loadMillis() {
        return loadMillis;
    }

    public QcProperties props() {
        return props;
    }

    public synchronized void reload() throws IOException {
        long t0 = System.currentTimeMillis();
        CorrectionResources res = switch (props.getSource()) {
            case SAMPLE -> Artifacts.Sample.build(Artifacts.Sample.world(), model(), log::info).resources();
            case FILES -> Artifacts.load(Path.of(props.getDataDir()), log::info).withModel(model());
            case ES -> loadFromEs();
        };
        EngineConfig cfg = new EngineConfig();
        cfg.countryWhitelist = new HashSet<>(props.getCountryWhitelist());
        cfg.judgeEnabled = props.isJudgeEnabled();
        cfg.deadlineMicros = props.getDeadlineMicros();
        current.set(new QueryCorrector(res, cfg));
        loadedAt = System.currentTimeMillis();
        loadMillis = loadedAt - t0;
        log.info("correction resources ready: source={}, countries={}, model={}, {} ms", props.getSource(),
                res.countries().keySet(), res.model().version(), loadMillis);
    }

    private LinearModel model() throws IOException {
        if (props.getModelFile() != null && !props.getModelFile().isBlank()) {
            return LinearModel.load(Path.of(props.getModelFile()));
        }
        Path inDir = Path.of(props.getDataDir()).resolve("model.json");
        if (props.getSource() != QcProperties.Source.SAMPLE && Files.exists(inDir)) {
            return LinearModel.load(inDir);
        }
        return Artifacts.bundledModel();
    }

    private CorrectionResources loadFromEs() throws IOException {
        QcProperties.Es es = props.getEs();
        EsLexiconLoader loader = new EsLexiconLoader(new EsClient(es.getUrl(), es.getUser(), es.getPassword()),
                es.getLexiconIndex(), EsLexiconLoader.Fields.defaults(), 5000);
        Map<Integer, List<TokenRecord>> lex = new HashMap<>();
        for (Integer c : es.getCountries()) {
            lex.put(c, loader.load(c, log::info));
        }
        Path dir = Path.of(props.getDataDir());
        Map<Integer, BigramModel> bigrams = Files.exists(dir.resolve("bigram.tsv.gz"))
                ? BigramBuilder.read(dir.resolve("bigram.tsv.gz"), Artifacts.BIGRAM_MU) : Map.of();
        ErrorDictionary ed = Files.exists(dir.resolve("errdict.tsv"))
                ? ErrorDictionary.read(dir.resolve("errdict.tsv")) : ErrorDictionary.empty();
        ChannelModel channel = Files.exists(dir.resolve("channel.tsv"))
                ? ChannelModel.fromCounts(EditCounts.read(dir.resolve("channel.tsv")), Artifacts.CHANNEL_KAPPA)
                : ChannelModel.defaults();
        return Artifacts.assemble(lex, bigrams, ed, channel, model(), log::info);
    }
}
