package com.mapsearch.qc.engine;

import com.mapsearch.qc.text.CasePattern;
import com.mapsearch.qc.text.QToken;
import com.mapsearch.qc.text.QueryTokenizer;
import com.mapsearch.qc.text.TokenType;
import com.mapsearch.qc.text.TokenizedQuery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point of the online correction:
 * tokenize -> per-token recall + features -> lattice decoding with context -> calibrated confidence ->
 * apply MEDIUM/HIGH corrections -> splice back into the original string (case, spacing, punctuation preserved).
 * Thread-safe; holds an immutable resource snapshot.
 */
public final class QueryCorrector {

    private final CorrectionResources res;
    private final EngineConfig cfg;
    private final LatticeBuilder builder;

    public QueryCorrector(CorrectionResources res, EngineConfig cfg) {
        this.res = res;
        this.cfg = cfg;
        this.builder = new LatticeBuilder(res, cfg);
    }

    public CorrectionResources resources() {
        return res;
    }

    public EngineConfig config() {
        return cfg;
    }

    public LatticeBuilder builder() {
        return builder;
    }

    public TokenizedQuery tokenize(CorrectionRequest req) {
        TokenizedQuery tq = QueryTokenizer.tokenize(req.query());
        if (req.lastTokenIncomplete() != null && !tq.tokens().isEmpty()) {
            boolean lastIsWord = tq.tokens().get(tq.size() - 1).type() == TokenType.WORD;
            tq = new TokenizedQuery(tq.original(), tq.tokens(), req.lastTokenIncomplete() && lastIsWord);
        }
        return tq;
    }

    public CorrectionResult correct(CorrectionRequest req) {
        long t0 = System.nanoTime();
        String q = req.query() == null ? "" : req.query();
        CountryResources cr = res.country(req.countryId());
        if (cr == null || !cfg.countryWhitelist.isEmpty() && !cfg.countryWhitelist.contains(req.countryId())) {
            return unchanged(q, "country_not_enabled", t0);
        }
        TokenizedQuery tq = tokenize(req);
        int letters = 0;
        for (QToken t : tq.tokens()) {
            if (t.type() == TokenType.WORD) {
                letters += t.norm().length();
            }
        }
        if (tq.tokens().isEmpty() || letters < cfg.minQueryLength) {
            return unchanged(q, "too_short", t0);
        }
        if (tq.size() > cfg.maxTokens) {
            return unchanged(q, "too_many_tokens", t0);
        }
        long deadline = t0 + cfg.deadlineMicros * 1000;
        Lattice lat = builder.build(tq, cr, req.cityId(), deadline);
        LatticeDecoder decoder = new LatticeDecoder(builder, cr);
        LatticeDecoder.Result best = decoder.decode(lat);

        List<CorrectionResult.TokenCorrection> corrections = new ArrayList<>();
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        double minConf = 1.0;
        boolean changed = false;
        for (Candidate c : best.path) {
            if (c.keep) {
                continue;
            }
            double[] conf = confidence(lat, best, c);
            double p = res.model().confidence(conf[0], conf[1]);
            CorrectionResult.Level level = level(p);
            boolean apply = level == CorrectionResult.Level.MEDIUM || level == CorrectionResult.Level.HIGH;
            QToken first = tq.tokens().get(c.start);
            QToken last = tq.tokens().get(c.end - 1);
            String from = q.substring(first.start(), last.end());
            String to = CasePattern.apply(first.raw(), c.output);
            corrections.add(new CorrectionResult.TokenCorrection(c.start, c.end, from, to, round(p), level, apply,
                    c.edits, c.sources.stream().map(Enum::name).toList()));
            if (apply) {
                out.append(q, cursor, first.start()).append(to);
                cursor = last.end();
                minConf = Math.min(minConf, p);
                changed = true;
            }
        }
        out.append(q.substring(cursor));
        List<CorrectionResult.TokenDebug> debug = req.debug() ? debug(lat, best) : null;
        long micros = (System.nanoTime() - t0) / 1000;
        if (!changed) {
            return new CorrectionResult(q, q, false, 0, CorrectionResult.Level.NONE, corrections, null, micros, debug);
        }
        return new CorrectionResult(q, out.toString(), true, round(minConf), level(minConf), corrections, null,
                micros, debug);
    }

    /** @return {margin over keeping the original span, margin over the best alternative correction} */
    static double[] confidence(Lattice lat, LatticeDecoder.Result r, Candidate c) {
        double mm = r.maxMarginal(c);
        double keepMm = Double.NEGATIVE_INFINITY;
        for (int i = c.start; i < c.end; i++) {
            if (lat.keep[i] != null) {
                keepMm = Math.max(keepMm, r.maxMarginal(lat.keep[i]));
            }
        }
        double alt = Double.NEGATIVE_INFINITY;
        for (int s = c.start; s < c.end; s++) {
            for (Candidate d : lat.byStart.get(s)) {
                if (d != c && !d.keep) {
                    alt = Math.max(alt, r.maxMarginal(d));
                }
            }
        }
        double mKeep = keepMm == Double.NEGATIVE_INFINITY ? 20 : mm - keepMm;
        double mAlt = alt == Double.NEGATIVE_INFINITY ? 20 : mm - alt;
        return new double[]{mKeep, mAlt};
    }

    private CorrectionResult.Level level(double p) {
        if (p >= res.model().highThreshold()) {
            return CorrectionResult.Level.HIGH;
        }
        if (p >= res.model().mediumThreshold()) {
            return CorrectionResult.Level.MEDIUM;
        }
        return CorrectionResult.Level.LOW;
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private CorrectionResult unchanged(String q, String reason, long t0) {
        return new CorrectionResult(q, q, false, 0, CorrectionResult.Level.NONE, List.of(), reason,
                (System.nanoTime() - t0) / 1000, null);
    }

    private List<CorrectionResult.TokenDebug> debug(Lattice lat, LatticeDecoder.Result best) {
        List<CorrectionResult.TokenDebug> out = new ArrayList<>();
        for (int i = 0; i < lat.n; i++) {
            QToken t = lat.query.tokens().get(i);
            List<Candidate> cands = new ArrayList<>(lat.byStart.get(i));
            cands.sort(Comparator.comparingDouble((Candidate c) -> -best.maxMarginal(c)));
            List<CorrectionResult.CandidateDebug> cd = new ArrayList<>();
            for (Candidate c : cands) {
                Map<String, Double> f = new LinkedHashMap<>();
                for (int k = 0; k < Features.SIZE; k++) {
                    if (c.features[k] != 0) {
                        f.put(Features.NAMES[k], round(c.features[k]));
                    }
                }
                cd.add(new CorrectionResult.CandidateDebug(c.output + (c.end - c.start > 1 ? " (merge)" : ""),
                        c.keep, c.sources.stream().map(Enum::name).toList(), c.edits, round(c.channelCost),
                        round(c.nodeScore), round(best.maxMarginal(c)), best.path.contains(c), f));
            }
            Lattice.TokenStatus st = lat.status[i];
            out.add(new CorrectionResult.TokenDebug(i, t.raw(), t.norm(), lat.query.isPrefixPosition(i),
                    st == null ? "NOT_CORRECTABLE" : st.name(), cd));
        }
        return out;
    }
}
