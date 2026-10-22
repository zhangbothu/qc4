package com.mapsearch.qc.engine;

import com.mapsearch.qc.channel.ChannelModel;
import com.mapsearch.qc.channel.PrefixAlign;
import com.mapsearch.qc.click.ErrorDictionary;
import com.mapsearch.qc.dict.CompactTrie;
import com.mapsearch.qc.dict.FuzzySearcher;
import com.mapsearch.qc.dict.Lexicon;
import com.mapsearch.qc.lm.BigramModel;
import com.mapsearch.qc.text.Phonetic;
import com.mapsearch.qc.text.QToken;
import com.mapsearch.qc.text.TokenType;
import com.mapsearch.qc.text.TokenizedQuery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-source candidate recall + feature extraction for every token of a query.
 *
 * <p>Recall sources (all in memory, no RPC): bounded-edit trie search (complete words, or prefixes for the
 * token being typed), Double Metaphone buckets, character trigrams for long tokens, the click-mined error
 * dictionary, and space errors (merge two tokens / split one token).</p>
 */
public final class LatticeBuilder {

    private static final ThreadLocal<FuzzySearcher> SEARCHER = ThreadLocal.withInitial(FuzzySearcher::new);
    /** -ln P(space error): merging / splitting tokens is treated as one fairly unlikely edit. */
    private static final double SPACE_EDIT_COST = 4.5;

    private final CorrectionResources res;
    private final EngineConfig cfg;

    public LatticeBuilder(CorrectionResources res, EngineConfig cfg) {
        this.res = res;
        this.cfg = cfg;
    }

    public Lattice build(TokenizedQuery tq, CountryResources cr, int cityId, long deadlineNanos) {
        Lattice lat = new Lattice(tq, cr.countryId(), cityId);
        for (int i = 0; i < tq.size(); i++) {
            List<Candidate> cands = forToken(lat, cr, i, System.nanoTime() > deadlineNanos);
            for (Candidate c : cands) {
                lat.add(c);
            }
        }
        if (cfg.splitMerge && System.nanoTime() < deadlineNanos) {
            for (int i = 0; i + 1 < tq.size(); i++) {
                Candidate m = mergeCandidate(lat, cr, i);
                if (m != null) {
                    lat.add(m);
                }
            }
        }
        return lat;
    }

    private boolean correctable(QToken t) {
        return t.type() == TokenType.WORD && t.norm().length() >= cfg.minTokenLength;
    }

    private List<Candidate> forToken(Lattice lat, CountryResources cr, int i, boolean outOfTime) {
        TokenizedQuery tq = lat.query;
        QToken tok = tq.tokens().get(i);
        String in = tok.norm();
        boolean prefix = tq.isPrefixPosition(i);
        Lexicon lex = cr.lexicon();
        CompactTrie trie = lex.trie();

        Candidate keep = new Candidate(i, i + 1, in, true, prefix && tok.type() == TokenType.WORD);
        keep.sources.add(Candidate.Source.KEEP);
        if (tok.type() == TokenType.WORD) {
            attachRep(keep, lex, -1);
        } else {
            keep.firstWord = null;
            keep.lastWord = null;
        }
        finish(keep, in, lat, cr);

        if (!correctable(tok) || outOfTime) {
            lat.status[i] = Lattice.TokenStatus.NOT_CORRECTABLE;
            return List.of(keep);
        }

        Map<String, Candidate> m = new LinkedHashMap<>();
        m.put(in, keep);

        if (cfg.judgeEnabled && trusted(keep, lex, lat.cityId)) {
            lat.status[i] = Lattice.TokenStatus.TRUSTED;
            return List.of(keep);
        }
        lat.status[i] = Lattice.TokenStatus.CANDIDATES;

        int radius = Math.min(cfg.maxTrieEdits, EditBudget.maxEdits(in.length()));
        for (FuzzySearcher.Match mt : SEARCHER.get().search(trie, in, radius, prefix, cfg.trieMaxVisits)) {
            if (prefix) {
                int rep = trie.bestWord(mt.node());
                String out = rep >= 0 ? PrefixAlign.alignedPrefix(in, lex.word(rep))
                        : mt.path().substring(0, Math.min(mt.path().length(), in.length()));
                offer(m, lat, cr, i, out, true, rep, Candidate.Source.TRIE_PREFIX);
            } else {
                offer(m, lat, cr, i, mt.path(), false, trie.wordId(mt.node()), Candidate.Source.TRIE);
            }
        }

        if (cfg.phoneticRecall && !prefix) {
            for (String code : new String[]{Phonetic.primary(in), Phonetic.alternate(in)}) {
                if (code.isEmpty()) {
                    continue;
                }
                for (int id : lex.byTone(code)) {
                    String w = lex.word(id);
                    if (Math.abs(w.length() - in.length()) <= 2
                            && ChannelModel.osa(in, w, false) <= EditBudget.maxEdits(in.length()) + 1) {
                        offer(m, lat, cr, i, w, false, id, Candidate.Source.PHONETIC);
                    }
                }
            }
        }

        if (cfg.trigramRecall && !prefix && in.length() >= 8) {
            for (int id : lex.trigrams().search(in, 0.45, 20_000, 30)) {
                String w = lex.word(id);
                if (ChannelModel.osa(in, w, false) <= EditBudget.maxEdits(in.length())) {
                    offer(m, lat, cr, i, w, false, id, Candidate.Source.TRIGRAM);
                }
            }
        }

        if (cfg.errorDictRecall) {
            for (ErrorDictionary.Correction k : res.errorDictionary().lookup(cr.countryId(), in)) {
                String out = prefix ? PrefixAlign.alignedPrefix(in, k.corrected()) : k.corrected();
                Candidate c = offer(m, lat, cr, i, out, prefix, -1, Candidate.Source.ERROR_DICT);
                if (c != null) {
                    c.errDictProb = Math.max(c.errDictProb, k.prob());
                    finish(c, in, lat, cr);
                }
            }
        }

        if (cfg.splitMerge && !prefix && in.length() >= 6) {
            addSplits(m, lat, cr, i, in);
        }

        List<Candidate> list = new ArrayList<>(m.values());
        list.removeIf(c -> !c.keep && !c.sources.contains(Candidate.Source.ERROR_DICT)
                && !c.sources.contains(Candidate.Source.SPLIT)
                && (c.edits == 0 || c.edits > EditBudget.maxEdits(in.length())));
        list.sort(Comparator.comparingDouble((Candidate c) -> -c.nodeScore));
        List<Candidate> top = new ArrayList<>();
        top.add(keep);
        for (Candidate c : list) {
            if (top.size() >= cfg.maxCandidatesPerToken) {
                break;
            }
            if (!c.keep) {
                top.add(c);
            }
        }
        return top;
    }

    private boolean trusted(Candidate keep, Lexicon lex, int cityId) {
        if (keep.prefixMode) {
            return keep.trieNode >= 0 && lex.trie().subtreeWeight(keep.trieNode) >= cfg.judgePrefixMass;
        }
        int id = lex.id(keep.output);
        if (id < 0) {
            return false;
        }
        long df = lex.docFreq(id);
        return df >= cfg.judgeDocFreq || df >= cfg.judgeExactDocFreq && lex.exposeFreq(id) > 0
                || df > cfg.judgeCityDocFreq && lex.cityFreq(id, cityId) > cfg.judgeCityFreq;
    }

    /** Adds (or enriches) a single-token candidate. */
    private Candidate offer(Map<String, Candidate> m, Lattice lat, CountryResources cr, int i, String out,
                            boolean prefix, int rep, Candidate.Source src) {
        String in = lat.query.tokens().get(i).norm();
        if (out.isEmpty() || out.equals(in)) {
            return null;
        }
        Candidate c = m.get(out);
        if (c == null) {
            c = new Candidate(i, i + 1, out, false, prefix);
            attachRep(c, cr.lexicon(), rep);
            finish(c, in, lat, cr);
            m.put(out, c);
        } else if (rep >= 0 && c.lexId >= 0 && rep != c.lexId && betterRep(cr.lexicon(), in, rep, c)) {
            c.lexId = rep;
            finish(c, in, lat, cr);
        }
        c.sources.add(src);
        return c;
    }

    private boolean betterRep(Lexicon lex, String in, int rep, Candidate c) {
        int a = ChannelModel.osa(in, lex.word(rep), c.prefixMode);
        int b = ChannelModel.osa(in, lex.word(c.lexId), c.prefixMode);
        return a < b || a == b && lex.weight(rep) > lex.weight(c.lexId);
    }

    private static void attachRep(Candidate c, Lexicon lex, int rep) {
        if (c.prefixMode) {
            c.trieNode = lex.trie().find(c.output);
            c.lexId = rep >= 0 ? rep : c.trieNode >= 0 ? lex.trie().bestWord(c.trieNode) : -1;
        } else {
            int id = lex.id(c.output);
            c.lexId = id >= 0 ? id : rep;
        }
    }

    private void addSplits(Map<String, Candidate> m, Lattice lat, CountryResources cr, int i, String in) {
        Lexicon lex = cr.lexicon();
        BigramModel bg = cr.bigram();
        int self = lex.id(in);
        if (self >= 0 && lex.docFreq(self) > 20) {
            return;
        }
        List<long[]> found = new ArrayList<>();
        for (int k = 2; k <= in.length() - 2; k++) {
            long c = bg.count(in.substring(0, k), in.substring(k));
            if (c > 0) {
                found.add(new long[]{k, c});
            }
        }
        found.sort((a, b) -> Long.compare(b[1], a[1]));
        for (int j = 0; j < found.size() && j < 2; j++) {
            int k = (int) found.get(j)[0];
            String left = in.substring(0, k);
            String right = in.substring(k);
            Candidate c = new Candidate(i, i + 1, left + " " + right, false, false);
            int lid = lex.id(left);
            int rid = lex.id(right);
            c.lexId = left.length() >= right.length() ? (lid >= 0 ? lid : rid) : (rid >= 0 ? rid : lid);
            c.sources.add(Candidate.Source.SPLIT);
            finish(c, in, lat, cr);
            m.put(c.output, c);
        }
    }

    private Candidate mergeCandidate(Lattice lat, CountryResources cr, int i) {
        TokenizedQuery tq = lat.query;
        QToken a = tq.tokens().get(i);
        QToken b = tq.tokens().get(i + 1);
        if (a.type() != TokenType.WORD || b.type() != TokenType.WORD) {
            return null;
        }
        String merged = a.norm() + b.norm();
        if (merged.length() < 5) {
            return null;
        }
        Lexicon lex = cr.lexicon();
        boolean prefix = tq.isPrefixPosition(i + 1);
        Candidate c = new Candidate(i, i + 2, merged, false, prefix);
        if (prefix) {
            int node = lex.trie().find(merged);
            if (node < 0 || lex.trie().subtreeWeight(node) < 5) {
                return null;
            }
        } else if (lex.id(merged) < 0) {
            return null;
        }
        attachRep(c, lex, -1);
        c.sources.add(Candidate.Source.MERGE);
        finish(c, a.norm() + " " + b.norm(), lat, cr);
        return c;
    }

    /** Builds a candidate for an arbitrary output (training: the gold label may be outside the recall set). */
    public Candidate forced(Lattice lat, CountryResources cr, int i, String out) {
        QToken tok = lat.query.tokens().get(i);
        boolean prefix = lat.query.isPrefixPosition(i);
        boolean isKeep = out.equals(tok.norm());
        if (isKeep && lat.keep[i] != null) {
            return lat.keep[i];
        }
        Candidate c = new Candidate(i, i + 1, out, isKeep, prefix);
        c.sources.add(Candidate.Source.FORCED);
        attachRep(c, cr.lexicon(), -1);
        for (ErrorDictionary.Correction k : res.errorDictionary().lookup(cr.countryId(), tok.norm())) {
            String o = prefix ? PrefixAlign.alignedPrefix(tok.norm(), k.corrected()) : k.corrected();
            if (o.equals(out)) {
                c.errDictProb = Math.max(c.errDictProb, k.prob());
            }
        }
        finish(c, tok.norm(), lat, cr);
        return c;
    }

    /** Computes edit statistics, features and the node score. {@code in} is the typed text of the span. */
    private void finish(Candidate c, String in, Lattice lat, CountryResources cr) {
        Lexicon lex = cr.lexicon();
        boolean split = c.sources.contains(Candidate.Source.SPLIT) || c.output.indexOf(' ') >= 0;
        boolean merge = c.end - c.start > 1;
        if (c.keep) {
            c.edits = 0;
            c.channelCost = 0;
        } else if (split || merge) {
            c.edits = 1;
            c.channelCost = SPACE_EDIT_COST;
        } else if (c.prefixMode) {
            String target = c.lexId >= 0 && lex.word(c.lexId).startsWith(c.output) ? lex.word(c.lexId) : c.output;
            c.edits = ChannelModel.osa(in, target, true);
            c.channelCost = res.channel().cost(in, target, true);
        } else {
            c.edits = ChannelModel.osa(in, c.output, false);
            c.channelCost = res.channel().cost(in, c.output, false);
        }

        double[] f = new double[Features.SIZE];
        int typedLen = in.replace(" ", "").length();
        f[Features.CHANGE] = c.keep ? 0 : 1;
        f[Features.CHANNEL] = -c.channelCost;
        f[Features.EDITS] = c.edits;
        f[Features.EDIT_RATIO] = c.edits / (double) Math.max(1, typedLen);

        double mass;
        if (c.firstWord == null) {
            mass = 0;
        } else if (split) {
            mass = Math.min(wordMass(lex, cr.bigram(), c.firstWord), wordMass(lex, cr.bigram(), c.lastWord));
        } else if (c.prefixMode) {
            mass = c.trieNode >= 0 ? lex.trie().subtreeWeight(c.trieNode) : 0;
        } else {
            int id = lex.id(c.output);
            mass = id >= 0 ? lex.weight(id) : 0;
        }
        f[Features.LOG_FREQ] = Math.log((mass + 0.5) / lex.totalWeight());
        f[Features.OOV] = mass > 0 ? 0 : 1;
        if (c.lexId >= 0 && mass > 0) {
            f[Features.CITY_FREQ] = Math.log1p(lex.cityFreq(c.lexId, lat.cityId));
            f[Features.CITY_SPREAD] = Math.log1p(lex.cityNum(c.lexId));
            f[Features.EXPOSE] = Math.log1p(lex.exposeFreq(c.lexId));
        }
        if (c.errDictProb > 0) {
            f[Features.ERRDICT_LOGP] = Math.log(c.errDictProb);
            f[Features.ERRDICT_HIT] = 1;
        }
        if (!c.keep && !split && !merge) {
            f[Features.TONE_MATCH] = Phonetic.soundsAlike(in, c.output) ? 1 : 0;
            f[Features.FIRST_CHAR] = c.output.charAt(0) != in.charAt(0) ? 1 : 0;
        }
        f[Features.LEN_DIFF] = Math.abs(c.output.replace(" ", "").length() - typedLen);
        f[Features.SPLIT_MERGE] = split || merge ? 1 : 0;
        f[Features.PREFIX_CHANGE] = c.prefixMode && !c.keep ? 1 : 0;
        if (split && cfg.contextModel) {
            f[Features.CTX_PMI] += cr.bigram().pmi(c.firstWord, c.lastWord);
            f[Features.CTX_SEEN] += 1;
        }
        c.features = f;
        c.nodeScore = res.model().score(f);
    }

    private static double wordMass(Lexicon lex, BigramModel bg, String w) {
        int id = lex.id(w);
        if (id >= 0) {
            return lex.weight(id);
        }
        return bg.unigram(w);
    }

    /** Context features between two adjacent spans: {pmi, seen, unknown}. */
    public double[] edgeFeatures(CountryResources cr, Candidate p, Candidate c) {
        double[] e = new double[3];
        if (!cfg.contextModel) {
            return e;
        }
        String a = p.lastWord;
        String b = c.firstWord;
        BigramModel bg = cr.bigram();
        if (a == null || b == null || bg.id(a) < 0) {
            e[2] = 1;
            return e;
        }
        if (c.prefixMode && c.output.indexOf(' ') < 0) {
            if (bg.prefixMass(b) == 0) {
                e[2] = 1;
                return e;
            }
            e[0] = bg.pmiPrefix(a, b);
            e[1] = bg.countPrefix(a, b) > 0 ? 1 : 0;
        } else {
            if (bg.id(b) < 0) {
                e[2] = 1;
                return e;
            }
            e[0] = bg.pmi(a, b);
            e[1] = bg.count(a, b) > 0 ? 1 : 0;
        }
        return e;
    }

    public double edgeScore(double[] e) {
        LinearModel m = res.model();
        return m.weight(Features.CTX_PMI) * e[0] + m.weight(Features.CTX_SEEN) * e[1]
                + m.weight(Features.CTX_UNKNOWN) * e[2];
    }
}
