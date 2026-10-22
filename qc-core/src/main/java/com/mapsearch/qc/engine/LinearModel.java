package com.mapsearch.qc.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Weights of the log-linear path score plus the confidence calibration
 * {@code P(correct) = sigmoid(aKeep * marginOverKeep + aAlt * marginOverRunnerUp + bias)} and the
 * decision thresholds (MEDIUM / HIGH are applied, LOW is not).
 */
public final class LinearModel {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final double[] w;
    private final double aKeep;
    private final double aAlt;
    private final double bias;
    private final double mediumThreshold;
    private final double highThreshold;
    private final String version;

    public LinearModel(double[] w, double aKeep, double aAlt, double bias, double mediumThreshold,
                       double highThreshold, String version) {
        this.w = Arrays.copyOf(w, Features.SIZE);
        this.aKeep = aKeep;
        this.aAlt = aAlt;
        this.bias = bias;
        this.mediumThreshold = mediumThreshold;
        this.highThreshold = highThreshold;
        this.version = version;
    }

    /** Hand-set starting point (also the initialization of perceptron training). */
    public static LinearModel defaults() {
        double[] w = new double[Features.SIZE];
        w[Features.CHANGE] = -1.0;
        w[Features.CHANNEL] = 0.6;
        w[Features.EDITS] = -0.5;
        w[Features.EDIT_RATIO] = -2.0;
        w[Features.LOG_FREQ] = 0.5;
        w[Features.OOV] = -3.0;
        w[Features.CITY_FREQ] = 0.2;
        w[Features.CITY_SPREAD] = 0.05;
        w[Features.EXPOSE] = 0.1;
        w[Features.ERRDICT_LOGP] = 0.5;
        w[Features.ERRDICT_HIT] = 2.0;
        w[Features.TONE_MATCH] = 0.5;
        w[Features.FIRST_CHAR] = -0.8;
        w[Features.LEN_DIFF] = -0.3;
        w[Features.SPLIT_MERGE] = -1.0;
        w[Features.PREFIX_CHANGE] = -0.3;
        w[Features.CTX_PMI] = 0.4;
        w[Features.CTX_SEEN] = 0.8;
        w[Features.CTX_UNKNOWN] = 0.0;
        return new LinearModel(w, 0.8, 0.5, -1.0, 0.5, 0.8, "defaults");
    }

    public double score(double[] f) {
        double s = 0;
        for (int i = 0; i < f.length; i++) {
            s += w[i] * f[i];
        }
        return s;
    }

    public double confidence(double marginKeep, double marginAlt) {
        double z = aKeep * marginKeep + aAlt * Math.min(marginAlt, 20) + bias;
        return 1.0 / (1.0 + Math.exp(-z));
    }

    public double[] weights() {
        return Arrays.copyOf(w, w.length);
    }

    public double weight(int i) {
        return w[i];
    }

    public double mediumThreshold() {
        return mediumThreshold;
    }

    public double highThreshold() {
        return highThreshold;
    }

    public double aKeep() {
        return aKeep;
    }

    public double aAlt() {
        return aAlt;
    }

    public double bias() {
        return bias;
    }

    public String version() {
        return version;
    }

    public LinearModel withWeights(double[] nw, String v) {
        return new LinearModel(nw, aKeep, aAlt, bias, mediumThreshold, highThreshold, v);
    }

    public LinearModel withWeight(int idx, double value) {
        double[] nw = weights();
        nw[idx] = value;
        return new LinearModel(nw, aKeep, aAlt, bias, mediumThreshold, highThreshold, version);
    }

    public LinearModel withCalibration(double ak, double aa, double b) {
        return new LinearModel(w, ak, aa, b, mediumThreshold, highThreshold, version);
    }

    public LinearModel withThresholds(double medium, double high) {
        return new LinearModel(w, aKeep, aAlt, bias, medium, high, version);
    }

    public void save(Path file) throws IOException {
        ObjectNode root = JSON.createObjectNode();
        root.put("version", version);
        ObjectNode ws = root.putObject("weights");
        for (int i = 0; i < Features.SIZE; i++) {
            ws.put(Features.NAMES[i], Math.round(w[i] * 1e5) / 1e5);
        }
        ObjectNode cal = root.putObject("calibration");
        cal.put("aKeep", aKeep);
        cal.put("aAlt", aAlt);
        cal.put("bias", bias);
        ObjectNode th = root.putObject("thresholds");
        th.put("medium", mediumThreshold);
        th.put("high", highThreshold);
        if (file.toAbsolutePath().getParent() != null) {
            Files.createDirectories(file.toAbsolutePath().getParent());
        }
        JSON.writeValue(file.toFile(), root);
    }

    public static LinearModel load(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return load(in);
        }
    }

    public static LinearModel load(InputStream in) throws IOException {
        JsonNode root = JSON.readTree(in);
        LinearModel d = defaults();
        double[] w = d.weights();
        JsonNode ws = root.path("weights");
        for (int i = 0; i < Features.SIZE; i++) {
            if (ws.has(Features.NAMES[i])) {
                w[i] = ws.get(Features.NAMES[i]).asDouble();
            }
        }
        JsonNode cal = root.path("calibration");
        JsonNode th = root.path("thresholds");
        return new LinearModel(w, cal.path("aKeep").asDouble(d.aKeep), cal.path("aAlt").asDouble(d.aAlt),
                cal.path("bias").asDouble(d.bias), th.path("medium").asDouble(d.mediumThreshold),
                th.path("high").asDouble(d.highThreshold), root.path("version").asText("unknown"));
    }
}
