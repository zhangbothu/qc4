package com.mapsearch.qc.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "qc")
public class QcProperties {

    public enum Source { SAMPLE, FILES, ES }

    /**
     * SAMPLE: synthetic MX/GB/MY/ID world built in memory at start-up (no external dependency).
     * FILES: artifacts directory produced by the offline jobs.
     * ES: lexicon pulled from poi_correcton_v2, other artifacts from {@link #dataDir}.
     */
    private Source source = Source.SAMPLE;
    private String dataDir = "data/artifacts";
    private String modelFile = "";
    private List<Integer> countryWhitelist = new ArrayList<>();
    private boolean judgeEnabled = true;
    private long deadlineMicros = 40_000;
    private Es es = new Es();

    public static class Es {
        private String url = "http://localhost:9200";
        private String user = "";
        private String password = "";
        private String lexiconIndex = "poi_correcton_v2";
        private List<Integer> countries = new ArrayList<>();

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getLexiconIndex() {
            return lexiconIndex;
        }

        public void setLexiconIndex(String lexiconIndex) {
            this.lexiconIndex = lexiconIndex;
        }

        public List<Integer> getCountries() {
            return countries;
        }

        public void setCountries(List<Integer> countries) {
            this.countries = countries;
        }
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getModelFile() {
        return modelFile;
    }

    public void setModelFile(String modelFile) {
        this.modelFile = modelFile;
    }

    public List<Integer> getCountryWhitelist() {
        return countryWhitelist;
    }

    public void setCountryWhitelist(List<Integer> countryWhitelist) {
        this.countryWhitelist = countryWhitelist;
    }

    public boolean isJudgeEnabled() {
        return judgeEnabled;
    }

    public void setJudgeEnabled(boolean judgeEnabled) {
        this.judgeEnabled = judgeEnabled;
    }

    public long getDeadlineMicros() {
        return deadlineMicros;
    }

    public void setDeadlineMicros(long deadlineMicros) {
        this.deadlineMicros = deadlineMicros;
    }

    public Es getEs() {
        return es;
    }

    public void setEs(Es es) {
        this.es = es;
    }
}
