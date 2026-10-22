package com.mapsearch.qc.dict;

import java.util.Arrays;

/**
 * Array-backed trie over the dictionary words of one country.
 * Children of a node are stored contiguously (BFS layout), so a node costs ~22 bytes and
 * there is no per-node object overhead (a few million tokens fit in a couple of hundred MB).
 *
 * <p>Every node also carries subtree aggregates which make prefix-aware correction cheap:
 * {@link #bestWord(int)} is the most popular word under the node and {@link #subtreeWeight(int)}
 * is the popularity mass of everything a user could still be typing from that prefix.</p>
 */
public final class CompactTrie {

    private final char[] label;
    private final int[] firstChild;
    private final byte[] childCount;
    private final int[] wordId;
    private final int[] bestWord;
    private final float[] subtreeWeight;
    private final int size;

    private CompactTrie(char[] label, int[] firstChild, byte[] childCount, int[] wordId, int[] bestWord,
                        float[] subtreeWeight, int size) {
        this.label = label;
        this.firstChild = firstChild;
        this.childCount = childCount;
        this.wordId = wordId;
        this.bestWord = bestWord;
        this.subtreeWeight = subtreeWeight;
        this.size = size;
    }

    public static final int ROOT = 0;

    /**
     * @param words  dictionary words, ascii a-z
     * @param weight popularity per word id
     */
    public static CompactTrie build(String[] words, double[] weight) {
        Integer[] order = new Integer[words.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> words[a].compareTo(words[b]));
        int[] sorted = new int[order.length];
        for (int i = 0; i < order.length; i++) {
            sorted[i] = order[i];
        }

        int cap = Math.max(16, words.length * 4);
        char[] label = new char[cap];
        int[] firstChild = new int[cap];
        byte[] childCount = new byte[cap];
        int[] wordId = new int[cap];
        int[] parent = new int[cap];
        Arrays.fill(wordId, -1);

        int[] qLo = new int[cap];
        int[] qHi = new int[cap];
        int[] qDepth = new int[cap];
        int size = 1;
        parent[0] = -1;
        qLo[0] = 0;
        qHi[0] = sorted.length;
        qDepth[0] = 0;

        for (int node = 0; node < size; node++) {
            int lo = qLo[node];
            int hi = qHi[node];
            int depth = qDepth[node];
            if (lo < hi && words[sorted[lo]].length() == depth) {
                wordId[node] = sorted[lo];
                lo++;
            }
            firstChild[node] = size;
            int count = 0;
            int i = lo;
            while (i < hi) {
                char c = words[sorted[i]].charAt(depth);
                int j = i + 1;
                while (j < hi && words[sorted[j]].charAt(depth) == c) {
                    j++;
                }
                if (size >= label.length) {
                    int ncap = label.length * 2;
                    label = Arrays.copyOf(label, ncap);
                    firstChild = Arrays.copyOf(firstChild, ncap);
                    childCount = Arrays.copyOf(childCount, ncap);
                    int old = wordId.length;
                    wordId = Arrays.copyOf(wordId, ncap);
                    Arrays.fill(wordId, old, ncap, -1);
                    parent = Arrays.copyOf(parent, ncap);
                    qLo = Arrays.copyOf(qLo, ncap);
                    qHi = Arrays.copyOf(qHi, ncap);
                    qDepth = Arrays.copyOf(qDepth, ncap);
                }
                label[size] = c;
                parent[size] = node;
                qLo[size] = i;
                qHi[size] = j;
                qDepth[size] = depth + 1;
                size++;
                count++;
                i = j;
            }
            childCount[node] = (byte) count;
        }

        int[] best = new int[size];
        float[] mass = new float[size];
        Arrays.fill(best, -1);
        for (int node = size - 1; node >= 0; node--) {
            int w = wordId[node];
            if (w >= 0) {
                mass[node] += (float) weight[w];
                if (best[node] < 0 || weight[w] > weight[best[node]]) {
                    best[node] = w;
                }
            }
            int p = parent[node];
            if (p >= 0) {
                mass[p] += mass[node];
                if (best[node] >= 0 && (best[p] < 0 || weight[best[node]] > weight[best[p]])) {
                    best[p] = best[node];
                }
            }
        }
        return new CompactTrie(Arrays.copyOf(label, size), Arrays.copyOf(firstChild, size),
                Arrays.copyOf(childCount, size), Arrays.copyOf(wordId, size), best, mass, size);
    }

    public int size() {
        return size;
    }

    public char label(int node) {
        return label[node];
    }

    public int firstChild(int node) {
        return firstChild[node];
    }

    public int childCount(int node) {
        return childCount[node] & 0xff;
    }

    public int wordId(int node) {
        return wordId[node];
    }

    public int bestWord(int node) {
        return bestWord[node];
    }

    public double subtreeWeight(int node) {
        return subtreeWeight[node];
    }

    public int child(int node, char c) {
        int from = firstChild[node];
        int to = from + (childCount[node] & 0xff);
        for (int i = from; i < to; i++) {
            if (label[i] == c) {
                return i;
            }
        }
        return -1;
    }

    /** @return the node reached by {@code prefix}, or -1 when no dictionary word starts with it */
    public int find(CharSequence prefix) {
        int node = ROOT;
        for (int i = 0; i < prefix.length() && node >= 0; i++) {
            node = child(node, prefix.charAt(i));
        }
        return node;
    }

    public long memoryBytes() {
        return (long) size * (2 + 4 + 1 + 4 + 4 + 4);
    }
}
