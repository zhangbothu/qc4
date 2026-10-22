package com.mapsearch.qc.es;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mapsearch.qc.dict.TokenRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Pulls one country's slice of {@code poi_correcton_v2} with {@code search_after} paging (sorted by tokenId).
 * Runs at service start-up and on scheduled refresh, never per request.
 */
public final class EsLexiconLoader {

    /** Field names of poi_correcton_v2; override if the mapping differs. */
    public record Fields(String tokenId, String tokenName, String countryId, String docFreq, String exposeFreq,
                         String cityNum, String topCities, String tone, String prefixes) {
        public static Fields defaults() {
            return new Fields("tokenId", "tokenName", "countryId", "totalDocFreq", "totalExposeFreq", "totalCityNum",
                    "topCities", "tone", "tokenPrefixes");
        }
    }

    private final EsClient es;
    private final String index;
    private final Fields f;
    private final int pageSize;

    public EsLexiconLoader(EsClient es, String index, Fields fields, int pageSize) {
        this.es = es;
        this.index = index;
        this.f = fields;
        this.pageSize = pageSize;
    }

    public List<TokenRecord> load(int countryId, Consumer<String> log) throws IOException {
        List<TokenRecord> out = new ArrayList<>();
        JsonNode after = null;
        while (true) {
            ObjectNode body = EsClient.json().createObjectNode();
            body.put("size", pageSize);
            body.putObject("query").putObject("term").put(f.countryId(), countryId);
            body.putArray("sort").addObject().put(f.tokenId(), "asc");
            if (after != null) {
                body.set("search_after", after);
            }
            JsonNode hits = es.search(index, body).path("hits").path("hits");
            if (!hits.isArray() || hits.isEmpty()) {
                break;
            }
            for (JsonNode h : hits) {
                TokenRecord r = parse(h.path("_source"), countryId);
                if (r != null) {
                    out.add(r);
                }
            }
            after = hits.get(hits.size() - 1).path("sort");
            if (hits.size() < pageSize) {
                break;
            }
            if (out.size() % (pageSize * 50) < pageSize) {
                log.accept("country " + countryId + ": loaded " + out.size() + " tokens");
            }
        }
        log.accept("country " + countryId + ": loaded " + out.size() + " tokens from " + index);
        return out;
    }

    TokenRecord parse(JsonNode s, int countryId) {
        String name = s.path(f.tokenName()).asText("");
        if (name.isEmpty()) {
            return null;
        }
        List<String> prefixes = new ArrayList<>();
        s.path(f.prefixes()).forEach(p -> prefixes.add(p.asText()));
        return new TokenRecord(s.path(f.tokenId()).asLong(), name.toLowerCase(), s.path(f.countryId()).asInt(countryId),
                s.path(f.docFreq()).asLong(), s.path(f.exposeFreq()).asLong(), s.path(f.cityNum()).asInt(),
                parseCities(s.path(f.topCities())), s.path(f.tone()).asText(""), prefixes);
    }

    /** Accepts {"123": 5}, ["123:5"], [{"cityId":123,"freq":5}] or [{"key":123,"value":5}]. */
    static Map<Integer, Integer> parseCities(JsonNode n) {
        Map<Integer, Integer> m = new HashMap<>();
        if (n.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = n.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                m.put(Integer.parseInt(e.getKey()), e.getValue().asInt());
            }
        } else if (n.isArray()) {
            for (JsonNode x : (ArrayNode) n) {
                if (x.isTextual() && x.asText().contains(":")) {
                    String[] kv = x.asText().split(":");
                    m.put(Integer.parseInt(kv[0].trim()), Integer.parseInt(kv[1].trim()));
                } else if (x.isObject()) {
                    JsonNode k = x.has("cityId") ? x.get("cityId") : x.get("key");
                    JsonNode v = x.has("freq") ? x.get("freq") : x.get("value");
                    if (k != null && v != null) {
                        m.put(k.asInt(), v.asInt());
                    }
                }
            }
        } else if (n.isTextual() && !n.asText().isEmpty()) {
            for (String kv : n.asText().split(",")) {
                String[] p = kv.split(":");
                if (p.length == 2) {
                    m.put(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()));
                }
            }
        }
        return m;
    }
}
