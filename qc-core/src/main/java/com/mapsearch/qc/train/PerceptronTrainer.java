package com.mapsearch.qc.train;

import com.mapsearch.qc.engine.Candidate;
import com.mapsearch.qc.engine.CorrectionRequest;
import com.mapsearch.qc.engine.CorrectionResources;
import com.mapsearch.qc.engine.CountryResources;
import com.mapsearch.qc.engine.EngineConfig;
import com.mapsearch.qc.engine.Features;
import com.mapsearch.qc.engine.Lattice;
import com.mapsearch.qc.engine.LatticeDecoder;
import com.mapsearch.qc.engine.LinearModel;
import com.mapsearch.qc.engine.QueryCorrector;
import com.mapsearch.qc.engine.TrainingHooks;
import com.mapsearch.qc.text.TokenizedQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Learns the path-score weights with an averaged structured perceptron: decode, and when the best path differs
 * from the labeled one, move the weights by {@code phi(gold) - phi(predicted)}. Because "keep" competes with
 * every correction inside the same lattice, the negatives (queries that must not be changed) directly teach
 * the model when <em>not</em> to correct — the main lever for correction precision.
 *
 * <p>After training, a logistic calibration maps the decoding margins to P(correction is right), and the MEDIUM
 * threshold is chosen to reach a target precision on held-out data.</p>
 */
public final class PerceptronTrainer {

    public record Report(LinearModel model, int usedSamples, int skippedSamples, List<String> log) {
    }

    private final EngineConfig cfg;
    private final Consumer<String> log;

    public PerceptronTrainer(EngineConfig cfg, Consumer<String> log) {
        this.cfg = cfg;
        this.log = log;
    }

    public Report train(CorrectionResources base, List<LabeledQuery> data, int epochs, double lr, double targetPrecision,
                        long seed) {
        List<LabeledQuery> shuffled = new ArrayList<>(data);
        Collections.shuffle(shuffled, new Random(seed));
        int calSize = Math.max(1, shuffled.size() / 5);
        List<LabeledQuery> cal = shuffled.subList(0, calSize);
        List<LabeledQuery> train = new ArrayList<>(shuffled.subList(calSize, shuffled.size()));

        LinearModel model = base.model();
        double[] w = model.weights();
        double[] sum = new double[Features.SIZE];
        long steps = 0;
        int skipped = 0;
        List<String> lines = new ArrayList<>();
        Random rnd = new Random(seed + 1);
        for (int epoch = 1; epoch <= epochs; epoch++) {
            Collections.shuffle(train, rnd);
            QueryCorrector qc = new QueryCorrector(base.withModel(model.withWeights(w, "training")), cfg);
            int errors = 0;
            skipped = 0;
            for (int k = 0; k < train.size(); k++) {
                if (k % 500 == 0 && k > 0) {
                    qc = new QueryCorrector(base.withModel(model.withWeights(w, "training")), cfg);
                }
                LabeledQuery s = train.get(k);
                double[] delta = update(qc, s);
                if (delta == null) {
                    skipped++;
                } else {
                    boolean nonZero = false;
                    for (int i = 0; i < Features.SIZE; i++) {
                        if (delta[i] != 0) {
                            nonZero = true;
                            w[i] += lr * delta[i];
                        }
                    }
                    if (nonZero) {
                        errors++;
                    }
                }
                for (int i = 0; i < Features.SIZE; i++) {
                    sum[i] += w[i];
                }
                steps++;
            }
            String line = String.format("epoch %d: path errors %d / %d (%.1f%%)", epoch, errors, train.size() - skipped,
                    100.0 * errors / Math.max(1, train.size() - skipped));
            lines.add(line);
            log.accept(line);
        }
        double[] avg = new double[Features.SIZE];
        for (int i = 0; i < Features.SIZE; i++) {
            avg[i] = sum[i] / Math.max(1, steps);
        }
        double scale = norm(LinearModel.defaults().weights()) / Math.max(1e-9, norm(avg));
        for (int i = 0; i < Features.SIZE; i++) {
            avg[i] *= scale;
        }
        LinearModel trained = model.withWeights(avg, "perceptron-" + epochs + "ep");
        trained = calibrate(base.withModel(trained), cal, targetPrecision, lines);
        return new Report(trained, train.size() - skipped, skipped, lines);
    }

    private static double norm(double[] v) {
        double s = 0;
        for (double x : v) {
            s += x * x;
        }
        return Math.sqrt(s);
    }

    /** @return phi(gold) - phi(pred), zero vector when correct, null when the sample cannot be used */
    private double[] update(QueryCorrector qc, LabeledQuery s) {
        CountryResources cr = qc.resources().country(s.countryId());
        if (cr == null) {
            return null;
        }
        TokenizedQuery tq = qc.tokenize(new CorrectionRequest(s.query(), s.countryId(), s.cityId(), null, false));
        String[] gold = GoldAligner.goldTokens(tq, s.expected());
        if (gold == null || tq.size() == 0 || tq.size() > qc.config().maxTokens) {
            return null;
        }
        Lattice lat = TrainingHooks.lattice(qc, tq, cr, s.cityId());
        LatticeDecoder dec = new LatticeDecoder(qc.builder(), cr);
        List<Candidate> pred = dec.decode(lat).path;
        if (matches(pred, gold)) {
            return new double[Features.SIZE];
        }
        List<Candidate> goldPath = new ArrayList<>();
        for (int i = 0; i < gold.length; i++) {
            Candidate g = null;
            for (Candidate c : lat.candidatesStartingAt(i)) {
                if (c.end == i + 1 && c.output.equals(gold[i])) {
                    g = c;
                    break;
                }
            }
            if (g == null) {
                g = qc.builder().forced(lat, cr, i, gold[i]);
            }
            goldPath.add(g);
        }
        double[] fg = dec.pathFeatures(goldPath);
        double[] fp = dec.pathFeatures(pred);
        double[] d = new double[Features.SIZE];
        for (int i = 0; i < Features.SIZE; i++) {
            d[i] = fg[i] - fp[i];
        }
        return d;
    }

    static boolean matches(List<Candidate> path, String[] gold) {
        if (path.size() != gold.length) {
            return false;
        }
        for (int i = 0; i < gold.length; i++) {
            Candidate c = path.get(i);
            if (c.end - c.start != 1 || !c.output.equals(gold[i])) {
                return false;
            }
        }
        return true;
    }

    /** Fits {aKeep, aAlt, bias} by logistic regression on proposed corrections, then picks thresholds. */
    LinearModel calibrate(CorrectionResources res, List<LabeledQuery> cal, double targetPrecision, List<String> lines) {
        QueryCorrector qc = new QueryCorrector(res, cfg);
        List<double[]> xs = new ArrayList<>();
        for (LabeledQuery s : cal) {
            CountryResources cr = res.country(s.countryId());
            if (cr == null) {
                continue;
            }
            TokenizedQuery tq = qc.tokenize(new CorrectionRequest(s.query(), s.countryId(), s.cityId(), null, false));
            String[] gold = GoldAligner.goldTokens(tq, s.expected());
            if (gold == null || tq.size() == 0 || tq.size() > cfg.maxTokens) {
                continue;
            }
            Lattice lat = TrainingHooks.lattice(qc, tq, cr, s.cityId());
            LatticeDecoder dec = new LatticeDecoder(qc.builder(), cr);
            LatticeDecoder.Result r = dec.decode(lat);
            for (Candidate c : r.path) {
                if (c.keep) {
                    continue;
                }
                double[] m = TrainingHooks.margins(lat, r, c);
                boolean right = c.end - c.start == 1 && c.output.equals(gold[c.start]);
                xs.add(new double[]{m[0], Math.min(m[1], 20), right ? 1 : 0});
            }
        }
        for (int label = 1; label >= 0; label--) {
            final int lb = label;
            double[] mk = xs.stream().filter(x -> x[2] == lb).mapToDouble(x -> x[0]).sorted().toArray();
            double[] ma = xs.stream().filter(x -> x[2] == lb).mapToDouble(x -> x[1]).sorted().toArray();
            if (mk.length > 0) {
                String line = String.format("  %s proposals: n=%d marginKeep p10/p50/p90=%.2f/%.2f/%.2f "
                                + "marginAlt p10/p50/p90=%.2f/%.2f/%.2f", lb == 1 ? "right" : "wrong", mk.length,
                        mk[mk.length / 10], mk[mk.length / 2], mk[mk.length * 9 / 10],
                        ma[ma.length / 10], ma[ma.length / 2], ma[ma.length * 9 / 10]);
                log.accept(line);
            }
        }
        double[] beta = fitLogistic(xs, 1.0);
        double ak = beta[0];
        double aa = beta[1];
        double b = beta[2];
        LinearModel m = res.model().withCalibration(ak, aa, b);
        double medium = 0.5;
        for (double t = 0.50; t <= 0.951; t += 0.01) {
            int tp = 0;
            int all = 0;
            for (double[] x : xs) {
                if (m.confidence(x[0], x[1]) >= t) {
                    all++;
                    tp += (int) x[2];
                }
            }
            if (all > 0 && (double) tp / all >= targetPrecision) {
                medium = t;
                break;
            }
            medium = t;
        }
        double high = Math.min(0.97, Math.max(medium + 0.15, 0.85));
        String line = String.format("calibration on %d proposals: aKeep=%.3f aAlt=%.3f bias=%.3f -> medium=%.2f high=%.2f",
                xs.size(), ak, aa, b, medium, high);
        lines.add(line);
        log.accept(line);
        return m.withThresholds(medium, high);
    }

    /** L2-regularized logistic regression on rows {x0, x1, label} with intercept, solved by Newton (IRLS). */
    static double[] fitLogistic(List<double[]> rows, double l2) {
        double[] beta = new double[3];
        for (int it = 0; it < 50; it++) {
            double[] g = new double[3];
            double[][] h = new double[3][3];
            for (double[] r : rows) {
                double[] x = {r[0], r[1], 1};
                double z = beta[0] * x[0] + beta[1] * x[1] + beta[2];
                double p = 1 / (1 + Math.exp(-z));
                double wgt = Math.max(p * (1 - p), 1e-6);
                for (int i = 0; i < 3; i++) {
                    g[i] += (p - r[2]) * x[i];
                    for (int j = 0; j < 3; j++) {
                        h[i][j] += wgt * x[i] * x[j];
                    }
                }
            }
            for (int i = 0; i < 2; i++) {
                g[i] += l2 * beta[i];
                h[i][i] += l2;
            }
            h[2][2] += 1e-6;
            double[] step = solve3(h, g);
            double norm = 0;
            for (int i = 0; i < 3; i++) {
                beta[i] -= step[i];
                norm += step[i] * step[i];
            }
            if (norm < 1e-12) {
                break;
            }
        }
        return beta;
    }

    private static double[] solve3(double[][] a, double[] b) {
        double[][] m = new double[3][4];
        for (int i = 0; i < 3; i++) {
            System.arraycopy(a[i], 0, m[i], 0, 3);
            m[i][3] = b[i];
        }
        for (int c = 0; c < 3; c++) {
            int piv = c;
            for (int r = c + 1; r < 3; r++) {
                if (Math.abs(m[r][c]) > Math.abs(m[piv][c])) {
                    piv = r;
                }
            }
            double[] t = m[c];
            m[c] = m[piv];
            m[piv] = t;
            if (Math.abs(m[c][c]) < 1e-12) {
                continue;
            }
            for (int r = 0; r < 3; r++) {
                if (r != c) {
                    double f = m[r][c] / m[c][c];
                    for (int k = c; k < 4; k++) {
                        m[r][k] -= f * m[c][k];
                    }
                }
            }
        }
        double[] x = new double[3];
        for (int i = 0; i < 3; i++) {
            x[i] = Math.abs(m[i][i]) < 1e-12 ? 0 : m[i][3] / m[i][i];
        }
        return x;
    }
}
