package com.mapsearch.qc.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact max-product decoding over the candidate lattice (first-order: node scores + adjacent-pair context).
 * Besides the best path it computes max-marginals {@code mm(c) = best score of any path through c}, which give
 * per-token margins "best vs keep original" and "best vs runner-up" used for confidence.
 */
public final class LatticeDecoder {

    public static final class Result {
        public final List<Candidate> path;
        public final double score;
        final double[] maxMarginal;

        Result(List<Candidate> path, double score, double[] maxMarginal) {
            this.path = path;
            this.score = score;
            this.maxMarginal = maxMarginal;
        }

        public double maxMarginal(Candidate c) {
            return maxMarginal[c.index];
        }
    }

    private final LatticeBuilder builder;
    private final CountryResources cr;
    private final Map<Long, double[]> edgeCache = new HashMap<>();

    public LatticeDecoder(LatticeBuilder builder, CountryResources cr) {
        this.builder = builder;
        this.cr = cr;
    }

    public double[] edge(Candidate p, Candidate c) {
        if (p.index < 0 || c.index < 0) {
            return builder.edgeFeatures(cr, p, c);
        }
        long key = ((long) p.index << 32) | c.index;
        return edgeCache.computeIfAbsent(key, k -> builder.edgeFeatures(cr, p, c));
    }

    private double edgeScore(Candidate p, Candidate c) {
        return builder.edgeScore(edge(p, c));
    }

    public Result decode(Lattice lat) {
        int m = lat.all.size();
        double[] alpha = new double[m];
        double[] beta = new double[m];
        int[] back = new int[m];
        Arrays.fill(back, -1);
        for (int s = 0; s < lat.n; s++) {
            for (Candidate c : lat.byStart.get(s)) {
                if (s == 0) {
                    alpha[c.index] = c.nodeScore;
                    continue;
                }
                double best = Double.NEGATIVE_INFINITY;
                int arg = -1;
                for (Candidate p : lat.byEnd.get(s)) {
                    double v = alpha[p.index] + edgeScore(p, c);
                    if (v > best) {
                        best = v;
                        arg = p.index;
                    }
                }
                alpha[c.index] = c.nodeScore + best;
                back[c.index] = arg;
            }
        }
        for (int s = lat.n - 1; s >= 0; s--) {
            for (Candidate c : lat.byStart.get(s)) {
                if (c.end == lat.n) {
                    beta[c.index] = 0;
                    continue;
                }
                double best = Double.NEGATIVE_INFINITY;
                for (Candidate q : lat.byStart.get(c.end)) {
                    best = Math.max(best, edgeScore(c, q) + q.nodeScore + beta[q.index]);
                }
                beta[c.index] = best;
            }
        }
        double bestScore = Double.NEGATIVE_INFINITY;
        int last = -1;
        for (Candidate c : lat.byEnd.get(lat.n)) {
            if (alpha[c.index] > bestScore) {
                bestScore = alpha[c.index];
                last = c.index;
            }
        }
        List<Candidate> path = new ArrayList<>();
        for (int k = last; k >= 0; k = back[k]) {
            path.add(lat.all.get(k));
        }
        Collections.reverse(path);
        double[] mm = new double[m];
        for (int i = 0; i < m; i++) {
            mm[i] = alpha[i] + beta[i];
        }
        return new Result(path, bestScore, mm);
    }

    /** Sum of node and edge features along a path (for perceptron updates). */
    public double[] pathFeatures(List<Candidate> path) {
        double[] f = new double[Features.SIZE];
        Candidate prev = null;
        for (Candidate c : path) {
            for (int i = 0; i < Features.SIZE; i++) {
                f[i] += c.features[i];
            }
            if (prev != null) {
                double[] e = edge(prev, c);
                f[Features.CTX_PMI] += e[0];
                f[Features.CTX_SEEN] += e[1];
                f[Features.CTX_UNKNOWN] += e[2];
            }
            prev = c;
        }
        return f;
    }
}
