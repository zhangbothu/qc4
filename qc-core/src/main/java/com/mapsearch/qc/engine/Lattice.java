package com.mapsearch.qc.engine;

import com.mapsearch.qc.text.TokenizedQuery;

import java.util.ArrayList;
import java.util.List;

/** Candidates for every token span of one query. Spans are single tokens, merges of two tokens, or splits. */
public final class Lattice {

    public enum TokenStatus { NOT_CORRECTABLE, TRUSTED, CANDIDATES }

    final TokenizedQuery query;
    final int n;
    final List<List<Candidate>> byStart;
    final List<List<Candidate>> byEnd;
    final Candidate[] keep;
    final TokenStatus[] status;
    final List<Candidate> all = new ArrayList<>();
    final int countryId;
    final int cityId;

    Lattice(TokenizedQuery query, int countryId, int cityId) {
        this.query = query;
        this.n = query.size();
        this.countryId = countryId;
        this.cityId = cityId;
        this.byStart = new ArrayList<>(n);
        this.byEnd = new ArrayList<>(n + 1);
        for (int i = 0; i < n; i++) {
            byStart.add(new ArrayList<>());
        }
        for (int i = 0; i <= n; i++) {
            byEnd.add(new ArrayList<>());
        }
        this.keep = new Candidate[n];
        this.status = new TokenStatus[n];
    }

    void add(Candidate c) {
        c.index = all.size();
        all.add(c);
        byStart.get(c.start).add(c);
        byEnd.get(c.end).add(c);
        if (c.keep && c.end == c.start + 1) {
            keep[c.start] = c;
        }
    }

    public TokenizedQuery query() {
        return query;
    }

    public int size() {
        return n;
    }

    public List<Candidate> candidatesStartingAt(int i) {
        return byStart.get(i);
    }

    public Candidate keep(int i) {
        return keep[i];
    }

    public TokenStatus status(int i) {
        return status[i];
    }

    public boolean hasAlternatives() {
        for (Candidate c : all) {
            if (!c.keep) {
                return true;
            }
        }
        return false;
    }

    public int candidateCount() {
        return all.size();
    }
}
