package com.mapsearch.qc.es;

import com.fasterxml.jackson.databind.JsonNode;
import com.mapsearch.qc.dict.TokenRecord;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EsLexiconLoaderTest {

    @Test
    void pagesWithSearchAfterAndParsesCityFormats() throws Exception {
        List<String> bodies = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/poi_correcton_v2/_search", ex -> {
            String req = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(req);
            JsonNode body = EsClient.json().readTree(req);
            String resp;
            if (!body.has("search_after")) {
                resp = """
                        {"hits":{"hits":[
                          {"_source":{"tokenId":1,"tokenName":"morelos","countryId":484,"totalDocFreq":900,
                            "totalExposeFreq":40,"totalCityNum":12,"topCities":{"4840002":300},"tone":"MRLS",
                            "tokenPrefixes":["more","morel"]},"sort":[1]},
                          {"_source":{"tokenId":2,"tokenName":"ecatepec","countryId":484,"totalDocFreq":500,
                            "totalExposeFreq":10,"totalCityNum":2,"topCities":["4840002:450","4840001:3"],"tone":"AKTP",
                            "tokenPrefixes":["ecat"]},"sort":[2]}
                        ]}}""";
            } else {
                resp = """
                        {"hits":{"hits":[
                          {"_source":{"tokenId":3,"tokenName":"insurgentes","countryId":484,"totalDocFreq":700,
                            "totalExposeFreq":0,"totalCityNum":5,"topCities":[{"cityId":4840001,"freq":600}],"tone":"ANSR"},
                           "sort":[3]}
                        ]}}""";
            }
            byte[] b = resp.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        });
        server.start();
        try {
            EsClient es = new EsClient("http://127.0.0.1:" + server.getAddress().getPort(), "", "");
            List<TokenRecord> recs = new EsLexiconLoader(es, "poi_correcton_v2", EsLexiconLoader.Fields.defaults(), 2)
                    .load(484, s -> { });
            assertEquals(3, recs.size());
            assertEquals(300, recs.get(0).topCities().get(4840002));
            assertEquals(450, recs.get(1).topCities().get(4840002));
            assertEquals(600, recs.get(2).topCities().get(4840001));
            assertEquals(2, bodies.size());
            assertEquals(2, EsClient.json().readTree(bodies.get(1)).get("search_after").get(0).asInt());
        } finally {
            server.stop(0);
        }
    }
}
