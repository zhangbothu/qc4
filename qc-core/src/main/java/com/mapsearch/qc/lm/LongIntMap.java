package com.mapsearch.qc.lm;

import java.util.Arrays;

/** Open-addressing long -> int map without boxing (bigram keys are packed (left << 32) | right ids). */
public final class LongIntMap {

    private static final long EMPTY = Long.MIN_VALUE;
    private long[] keys;
    private int[] values;
    private int size;
    private int mask;

    public LongIntMap(int expected) {
        int cap = Integer.highestOneBit(Math.max(4, (int) (expected / 0.6)) - 1) << 1;
        keys = new long[cap];
        values = new int[cap];
        Arrays.fill(keys, EMPTY);
        mask = cap - 1;
    }

    private static int hash(long k) {
        long h = k * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32));
    }

    public int get(long key) {
        int i = hash(key) & mask;
        while (true) {
            long k = keys[i];
            if (k == key) {
                return values[i];
            }
            if (k == EMPTY) {
                return 0;
            }
            i = (i + 1) & mask;
        }
    }

    public void add(long key, int delta) {
        if (size >= keys.length * 0.6) {
            rehash();
        }
        int i = hash(key) & mask;
        while (true) {
            long k = keys[i];
            if (k == key) {
                values[i] += delta;
                return;
            }
            if (k == EMPTY) {
                keys[i] = key;
                values[i] = delta;
                size++;
                return;
            }
            i = (i + 1) & mask;
        }
    }

    private void rehash() {
        long[] ok = keys;
        int[] ov = values;
        keys = new long[ok.length * 2];
        values = new int[ok.length * 2];
        Arrays.fill(keys, EMPTY);
        mask = keys.length - 1;
        size = 0;
        for (int i = 0; i < ok.length; i++) {
            if (ok[i] != EMPTY) {
                add(ok[i], ov[i]);
            }
        }
    }

    public int size() {
        return size;
    }

    public interface Visitor {
        void visit(long key, int value);
    }

    public void forEach(Visitor v) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != EMPTY) {
                v.visit(keys[i], values[i]);
            }
        }
    }
}
