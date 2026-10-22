package com.mapsearch.qc.dict;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded Damerau (OSA) edit-distance search over a {@link CompactTrie}, computing one DP row per trie level
 * and pruning a branch as soon as every cell of its row exceeds {@code maxEdit}.
 *
 * <ul>
 *   <li>complete mode: returns words {@code w} with {@code OSA(input, w) <= maxEdit}</li>
 *   <li>prefix mode: returns trie nodes {@code p} (any word prefix) with {@code OSA(input, p) <= maxEdit};
 *       this is what the user can legitimately be typing towards, so "coefe" matches the prefix "coffe".</li>
 * </ul>
 * Instances are not thread-safe (they reuse DP buffers); create one per request or use a ThreadLocal.
 */
public final class FuzzySearcher {

    public record Match(int node, String path, int cost) {
    }

    private static final int MAX_LEN = 48;

    private final int[][] rows = new int[MAX_LEN + 4][MAX_LEN + 2];
    private final char[] path = new char[MAX_LEN + 4];
    private CompactTrie trie;
    private String input;
    private int len;
    private int maxEdit;
    private boolean prefixMode;
    private int budget;
    private List<Match> out;

    public List<Match> search(CompactTrie trie, String input, int maxEdit, boolean prefixMode, int maxVisits) {
        List<Match> result = new ArrayList<>();
        if (input.length() > MAX_LEN || input.isEmpty()) {
            return result;
        }
        this.trie = trie;
        this.input = input;
        this.len = input.length();
        this.maxEdit = maxEdit;
        this.prefixMode = prefixMode;
        this.budget = maxVisits;
        this.out = result;
        int[] row0 = rows[0];
        for (int j = 0; j <= len; j++) {
            row0[j] = j;
        }
        int from = trie.firstChild(CompactTrie.ROOT);
        int to = from + trie.childCount(CompactTrie.ROOT);
        for (int c = from; c < to && budget > 0; c++) {
            visit(c, 1);
        }
        this.out = null;
        this.trie = null;
        return result;
    }

    private void visit(int node, int depth) {
        if (--budget < 0 || depth > len + maxEdit || depth > MAX_LEN + 2) {
            return;
        }
        char c = trie.label(node);
        path[depth - 1] = c;
        int[] prev = rows[depth - 1];
        int[] cur = rows[depth];
        cur[0] = depth;
        int rowMin = cur[0];
        for (int j = 1; j <= len; j++) {
            char in = input.charAt(j - 1);
            int v = Math.min(prev[j] + 1, cur[j - 1] + 1);
            v = Math.min(v, prev[j - 1] + (in == c ? 0 : 1));
            if (depth >= 2 && j >= 2 && in == path[depth - 2] && input.charAt(j - 2) == c) {
                v = Math.min(v, rows[depth - 2][j - 2] + 1);
            }
            cur[j] = v;
            if (v < rowMin) {
                rowMin = v;
            }
        }
        int cost = cur[len];
        if (cost <= maxEdit) {
            if (prefixMode || trie.wordId(node) >= 0) {
                out.add(new Match(node, new String(path, 0, depth), cost));
            }
        }
        if (rowMin > maxEdit) {
            return;
        }
        int from = trie.firstChild(node);
        int to = from + trie.childCount(node);
        for (int ch = from; ch < to; ch++) {
            visit(ch, depth + 1);
        }
    }
}
