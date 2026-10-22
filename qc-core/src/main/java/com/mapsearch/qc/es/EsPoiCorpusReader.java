package com.mapsearch.qc.es;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mapsearch.qc.corpus.PoiRecord;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Streams POIs of one country out of the mother index {@code poi_geo_abroad_v2} (scroll API) to build the
 * bigram context model offline. Field names are configurable because only the semantics of the mapping are known.
 */
public final class EsPoiCorpusReader {

    /**
     * @param names       multi-language name list (all names are used: each is its own token sequence)
     * @param aliases     core alias list
     * @param adminIds    admin division id list; {@code cityLevelIndex} selects the city-level id
     * @param typeCode    POI type hierarchy code; values starting with {@code postcodeTypePrefix} are skipped
     */
    public record Fields(String id, String countryId, String names, String aliases, String address, String adminIds,
                         int cityLevelIndex, String typeCode, String postcodeTypePrefix) {
        public static Fields defaults() {
            return new Fields("poi_id", "country_id", "names", "alias", "address", "admin_ids", 2, "type_code", "1907");
        }
    }

    private final EsClient es;
    private final String index;
    private final Fields f;

    public EsPoiCorpusReader(EsClient es, String index, Fields fields) {
        this.es = es;
        this.index = index;
        this.f = fields;
    }

    public long read(int countryId, Consumer<PoiRecord> sink, Consumer<String> log) throws IOException {
        ObjectNode body = EsClient.json().createObjectNode();
        body.put("size", 2000);
        body.putObject("query").putObject("term").put(f.countryId(), countryId);
        ArrayNode src = body.putArray("_source");
        src.add(f.id()).add(f.names()).add(f.aliases()).add(f.address()).add(f.adminIds()).add(f.typeCode());
        JsonNode resp = es.post("/" + index + "/_search?scroll=2m", body);
        long n = 0;
        while (true) {
            JsonNode hits = resp.path("hits").path("hits");
            if (!hits.isArray() || hits.isEmpty()) {
                break;
            }
            for (JsonNode h : hits) {
                JsonNode s = h.path("_source");
                boolean postcode = s.path(f.typeCode()).asText("").startsWith(f.postcodeTypePrefix());
                int city = s.path(f.adminIds()).isArray() && s.path(f.adminIds()).size() > f.cityLevelIndex()
                        ? s.path(f.adminIds()).get(f.cityLevelIndex()).asInt() : 0;
                String address = s.path(f.address()).isTextual() ? s.path(f.address()).asText()
                        : s.path(f.address()).toString();
                for (JsonNode name : iterable(s.path(f.names()))) {
                    sink.accept(new PoiRecord(s.path(f.id()).asLong(), countryId, city, name.asText(), address, postcode));
                    address = null;
                }
                for (JsonNode alias : iterable(s.path(f.aliases()))) {
                    sink.accept(new PoiRecord(s.path(f.id()).asLong(), countryId, city, alias.asText(), null, postcode));
                }
                n++;
            }
            if (n % 200_000 < hits.size()) {
                log.accept("country " + countryId + ": read " + n + " POIs");
            }
            ObjectNode next = EsClient.json().createObjectNode();
            next.put("scroll", "2m");
            next.put("scroll_id", resp.path("_scroll_id").asText());
            resp = es.post("/_search/scroll", next);
        }
        return n;
    }

    private static Iterable<JsonNode> iterable(JsonNode n) {
        if (n.isArray()) {
            return n;
        }
        if (n.isTextual() && !n.asText().isEmpty()) {
            return java.util.List.of(n);
        }
        return java.util.List.of();
    }

    /**
     * DSL to look up POIs by (partial) name near a location inside an admin division — handy to sanity-check a
     * correction against the mother index, e.g. when reviewing error-dictionary entries.
     */
    public static ObjectNode nameNearQuery(String nameField, String adminField, String geoField, String text,
                                           long adminId, double lat, double lon, String radius) {
        ObjectNode body = EsClient.json().createObjectNode();
        body.put("size", 10);
        ObjectNode bool = body.putObject("query").putObject("bool");
        bool.putArray("must").addObject().putObject("match_phrase_prefix").put(nameField, text);
        ArrayNode filter = bool.putArray("filter");
        filter.addObject().putObject("term").put(adminField, adminId);
        ObjectNode geo = filter.addObject().putObject("geo_distance");
        geo.put("distance", radius);
        geo.putObject(geoField).put("lat", lat).put("lon", lon);
        return body;
    }
}
