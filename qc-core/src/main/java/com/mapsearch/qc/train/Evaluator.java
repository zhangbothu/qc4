package com.mapsearch.qc.train;

import com.mapsearch.qc.engine.CorrectionRequest;
import com.mapsearch.qc.engine.CorrectionResult;
import com.mapsearch.qc.engine.QueryCorrector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Query-level metrics.
 * <ul>
 *   <li>precision (纠正准确率): among queries the engine changed, share whose output equals the label</li>
 *   <li>recall: among queries that needed a correction, share fixed exactly</li>
 *   <li>false-correction rate: among clean queries, share that were changed</li>
 *   <li>accuracy: share of all queries whose output equals the label</li>
 * </ul>
 */
public final class Evaluator {

    public static final class Metrics {
        public int total;
        public int needFix;
        public int clean;
        public int changed;
        public int changedRight;
        public int fixed;
        public int falseChange;
        public int exact;
        public long[] latencies;
        public final List<String> errors = new ArrayList<>();
        public final Map<Integer, int[]> byCountry = new TreeMap<>();

        public double precision() {
            return changed == 0 ? 0 : (double) changedRight / changed;
        }

        public double recall() {
            return needFix == 0 ? 0 : (double) fixed / needFix;
        }

        public double falseRate() {
            return clean == 0 ? 0 : (double) falseChange / clean;
        }

        public double accuracy() {
            return total == 0 ? 0 : (double) exact / total;
        }

        public double f1() {
            double p = precision();
            double r = recall();
            return p + r == 0 ? 0 : 2 * p * r / (p + r);
        }

        public long latencyPercentile(double p) {
            if (latencies == null || latencies.length == 0) {
                return 0;
            }
            long[] s = latencies.clone();
            Arrays.sort(s);
            return s[Math.min(s.length - 1, (int) Math.ceil(p * s.length) - 1)];
        }

        public String summary() {
            return String.format(Locale.ROOT,
                    "n=%d (needFix=%d, clean=%d) | precision=%.2f%% recall=%.2f%% F1=%.2f%% falseCorr=%.2f%% "
                            + "accuracy=%.2f%% | latency p50=%dus p99=%dus max=%dus",
                    total, needFix, clean, 100 * precision(), 100 * recall(), 100 * f1(), 100 * falseRate(),
                    100 * accuracy(), latencyPercentile(0.5), latencyPercentile(0.99), latencyPercentile(1.0));
        }

        public String countryBreakdown() {
            StringBuilder sb = new StringBuilder();
            byCountry.forEach((c, v) -> sb.append(String.format(Locale.ROOT,
                    "  country %d: n=%d precision=%.2f%% recall=%.2f%% falseCorr=%.2f%%%n", c, v[0],
                    v[3] == 0 ? 0 : 100.0 * v[4] / v[3], v[1] == 0 ? 0 : 100.0 * v[5] / v[1],
                    v[2] == 0 ? 0 : 100.0 * v[6] / v[2])));
            return sb.toString();
        }
    }

    private Evaluator() {
    }

    public static Metrics evaluate(QueryCorrector qc, List<LabeledQuery> data, int keepErrors) {
        for (int i = 0; i < Math.min(200, data.size()); i++) {
            LabeledQuery s = data.get(i);
            qc.correct(CorrectionRequest.of(s.query(), s.countryId(), s.cityId()));
        }
        Metrics m = new Metrics();
        m.latencies = new long[data.size()];
        for (int i = 0; i < data.size(); i++) {
            LabeledQuery s = data.get(i);
            CorrectionResult r = qc.correct(CorrectionRequest.of(s.query(), s.countryId(), s.cityId()));
            m.latencies[i] = r.latencyMicros();
            boolean need = s.needsCorrection();
            boolean right = r.corrected().equals(s.expected());
            int[] c = m.byCountry.computeIfAbsent(s.countryId(), k -> new int[7]);
            m.total++;
            c[0]++;
            if (need) {
                m.needFix++;
                c[1]++;
            } else {
                m.clean++;
                c[2]++;
            }
            if (r.changed()) {
                m.changed++;
                c[3]++;
                if (right) {
                    m.changedRight++;
                    c[4]++;
                }
                if (!need) {
                    m.falseChange++;
                    c[6]++;
                }
            }
            if (need && right) {
                m.fixed++;
                c[5]++;
            }
            if (right) {
                m.exact++;
            }
            if (!right && m.errors.size() < keepErrors) {
                m.errors.add(String.format("[%d] '%s' -> '%s' (expected '%s')", s.countryId(), s.query(),
                        r.corrected(), s.expected()));
            }
        }
        return m;
    }
}
