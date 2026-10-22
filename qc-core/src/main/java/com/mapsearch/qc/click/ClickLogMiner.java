package com.mapsearch.qc.click;

import com.mapsearch.qc.channel.ChannelModel;
import com.mapsearch.qc.channel.EditCounts;
import com.mapsearch.qc.channel.PrefixAlign;
import com.mapsearch.qc.engine.EditBudget;
import com.mapsearch.qc.text.CasePattern;
import com.mapsearch.qc.text.QToken;
import com.mapsearch.qc.text.QueryTokenizer;
import com.mapsearch.qc.text.TextNormalizer;
import com.mapsearch.qc.text.TokenType;
import com.mapsearch.qc.text.TokenizedQuery;
import com.mapsearch.qc.train.LabeledQuery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns "query + Google top-1 POI" logs into correction knowledge.
 *
 * <p>Each query token is aligned to the closest token of the Google result's name/address. The token being
 * typed is aligned as a prefix and the target is truncated to the typed length, so
 * {@code "roth nyrnber"} vs {@code "Roth Nürnberg"} yields the pair {@code nyrnber -> nurnber}
 * (no completion). Outputs:</p>
 * <ol>
 *   <li>an {@link ErrorDictionary} with P(correction | typed token), filtered by support and user count</li>
 *   <li>{@link EditCounts} to learn the character-level channel model</li>
 *   <li>labeled queries (positives <em>and</em> "leave it alone" negatives) for ranker training/calibration</li>
 * </ol>
 * Queries whose Google result does not explain every token within the edit budget (semantic matches,
 * different POI) are not used as training labels, which keeps label noise low.
 */
public final class ClickLogMiner {

    private final Map<Integer, Map<String, Map<String, long[]>>> pairs = new HashMap<>();
    private final Map<Integer, Map<String, Map<String, Set<String>>>> pairUsers = new HashMap<>();
    private final Map<Integer, Map<String, Long>> occurrences = new HashMap<>();
    private final EditCounts editCounts = new EditCounts();
    private final List<LabeledQuery> samples = new ArrayList<>();
    private long seen;
    private long usable;
    private long withError;

    public void add(ClickRecord r) {
        seen++;
        TokenizedQuery tq = QueryTokenizer.tokenize(r.query());
        Set<String> targets = new LinkedHashSet<>(TextNormalizer.dictTokens(r.googleName()));
        if (r.googleAddress() != null) {
            targets.addAll(TextNormalizer.dictTokens(r.googleAddress()));
        }
        if (targets.isEmpty() || tq.tokens().isEmpty()) {
            return;
        }
        boolean allAligned = true;
        boolean anyError = false;
        String[] replacement = new String[tq.size()];
        for (int i = 0; i < tq.size(); i++) {
            QToken t = tq.tokens().get(i);
            if (t.type() != TokenType.WORD) {
                continue;
            }
            String q = t.norm();
            occurrences.computeIfAbsent(r.countryId(), k -> new HashMap<>()).merge(q, 1L, Long::sum);
            boolean prefix = tq.isPrefixPosition(i);
            String best = null;
            int bestDist = Integer.MAX_VALUE;
            for (String target : targets) {
                int d = ChannelModel.osa(q, target, prefix);
                if (d < bestDist || (d == bestDist && best != null && sharedPrefix(q, target) > sharedPrefix(q, best))) {
                    bestDist = d;
                    best = target;
                }
            }
            if (bestDist == 0) {
                continue;
            }
            if (q.length() < 4 || bestDist > EditBudget.maxEdits(q.length())) {
                allAligned = false;
                continue;
            }
            String out = prefix ? PrefixAlign.alignedPrefix(q, best) : best;
            if (out.equals(q)) {
                continue;
            }
            anyError = true;
            replacement[i] = out;
            pairs.computeIfAbsent(r.countryId(), k -> new HashMap<>())
                    .computeIfAbsent(q, k -> new HashMap<>())
                    .computeIfAbsent(out, k -> new long[1])[0]++;
            pairUsers.computeIfAbsent(r.countryId(), k -> new HashMap<>())
                    .computeIfAbsent(q, k -> new HashMap<>())
                    .computeIfAbsent(out, k -> new HashSet<>()).add(r.uid());
            editCounts.addPair(best, q, prefix, 1);
        }
        if (!allAligned) {
            return;
        }
        usable++;
        if (anyError) {
            withError++;
        }
        StringBuilder expected = new StringBuilder();
        int last = 0;
        for (int i = 0; i < tq.size(); i++) {
            QToken t = tq.tokens().get(i);
            if (replacement[i] != null) {
                expected.append(r.query(), last, t.start()).append(CasePattern.apply(t.raw(), replacement[i]));
                last = t.end();
            }
        }
        expected.append(r.query().substring(last));
        samples.add(new LabeledQuery(r.countryId(), r.cityId(), r.query(), expected.toString()));
    }

    private static int sharedPrefix(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }

    /**
     * Pairs that come from the existing rule-based detector ("nyrnber" -> "nurnber") can be fed directly.
     */
    public void addMinedPair(int countryId, String uid, String error, String correction, boolean prefix) {
        String q = TextNormalizer.fold(error);
        String out = TextNormalizer.fold(correction);
        pairs.computeIfAbsent(countryId, k -> new HashMap<>()).computeIfAbsent(q, k -> new HashMap<>())
                .computeIfAbsent(out, k -> new long[1])[0]++;
        pairUsers.computeIfAbsent(countryId, k -> new HashMap<>()).computeIfAbsent(q, k -> new HashMap<>())
                .computeIfAbsent(out, k -> new HashSet<>()).add(uid);
        occurrences.computeIfAbsent(countryId, k -> new HashMap<>()).merge(q, 1L, Long::sum);
        editCounts.addPair(out, q, prefix, 1);
    }

    public ErrorDictionary errorDictionary(int minCount, int minUsers, double minProb) {
        Map<Integer, Map<String, List<ErrorDictionary.Correction>>> out = new HashMap<>();
        for (Map.Entry<Integer, Map<String, Map<String, long[]>>> c : pairs.entrySet()) {
            int country = c.getKey();
            for (Map.Entry<String, Map<String, long[]>> e : c.getValue().entrySet()) {
                long occ = occurrences.getOrDefault(country, Map.of()).getOrDefault(e.getKey(), 1L);
                List<ErrorDictionary.Correction> list = new ArrayList<>();
                for (Map.Entry<String, long[]> k : e.getValue().entrySet()) {
                    long cnt = k.getValue()[0];
                    int users = pairUsers.get(country).get(e.getKey()).get(k.getKey()).size();
                    double prob = (double) cnt / Math.max(occ, cnt);
                    if (cnt >= minCount && users >= minUsers && prob >= minProb) {
                        list.add(new ErrorDictionary.Correction(k.getKey(), cnt, users, prob));
                    }
                }
                if (!list.isEmpty()) {
                    list.sort((a, b) -> Double.compare(b.prob(), a.prob()));
                    out.computeIfAbsent(country, k -> new HashMap<>()).put(e.getKey(), list.subList(0, Math.min(3, list.size())));
                }
            }
        }
        return new ErrorDictionary(out);
    }

    public EditCounts editCounts() {
        return editCounts;
    }

    public List<LabeledQuery> samples() {
        return samples;
    }

    public String summary() {
        return String.format("click records=%d, fully aligned=%d, aligned with a typo=%d, distinct error tokens=%d, "
                        + "channel pairs=%d", seen, usable, withError,
                pairs.values().stream().mapToInt(Map::size).sum(), editCounts.pairs());
    }
}
