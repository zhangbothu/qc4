package com.mapsearch.qc.service;

import com.mapsearch.qc.engine.CorrectionRequest;
import com.mapsearch.qc.engine.CorrectionResources;
import com.mapsearch.qc.engine.CorrectionResult;
import com.mapsearch.qc.engine.CountryResources;
import com.mapsearch.qc.engine.Features;
import com.mapsearch.qc.engine.LinearModel;
import com.mapsearch.qc.engine.QueryCorrector;
import com.mapsearch.qc.sample.SampleWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class CorrectionController {

    private static final Logger log = LoggerFactory.getLogger(CorrectionController.class);

    public record CorrectBody(String query, Integer countryId, Integer cityId, Boolean lastTokenIncomplete,
                              Boolean debug) {
    }

    private final CorrectorHolder holder;

    public CorrectionController(CorrectorHolder holder) {
        this.holder = holder;
    }

    @GetMapping("/api/v1/correct")
    public CorrectionResult correctGet(@RequestParam("q") String q,
                                       @RequestParam("country") int country,
                                       @RequestParam(value = "city", defaultValue = "0") int city,
                                       @RequestParam(value = "incomplete", required = false) Boolean incomplete,
                                       @RequestParam(value = "debug", defaultValue = "false") boolean debug) {
        return run(new CorrectionRequest(q, country, city, incomplete, debug));
    }

    @PostMapping("/api/v1/correct")
    public ResponseEntity<?> correctPost(@RequestBody CorrectBody body) {
        if (body == null || body.query() == null || body.countryId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "query and countryId are required"));
        }
        return ResponseEntity.ok(run(toRequest(body)));
    }

    @PostMapping("/api/v1/correct/batch")
    public List<CorrectionResult> batch(@RequestBody List<CorrectBody> bodies) {
        List<CorrectionResult> out = new ArrayList<>(bodies.size());
        for (CorrectBody b : bodies) {
            out.add(b.query() == null || b.countryId() == null ? null : run(toRequest(b)));
        }
        return out;
    }

    private static CorrectionRequest toRequest(CorrectBody b) {
        return new CorrectionRequest(b.query(), b.countryId(), b.cityId() == null ? 0 : b.cityId(),
                b.lastTokenIncomplete(), Boolean.TRUE.equals(b.debug()));
    }

    /** Fail-open: any internal error returns the original query untouched. */
    private CorrectionResult run(CorrectionRequest req) {
        try {
            return holder.get().correct(req);
        } catch (RuntimeException e) {
            log.warn("correction failed for '{}' (country {})", req.query(), req.countryId(), e);
            return new CorrectionResult(req.query(), req.query(), false, 0, CorrectionResult.Level.NONE, List.of(),
                    "internal_error", 0, null);
        }
    }

    @GetMapping("/api/v1/meta")
    public Map<String, Object> meta() {
        QueryCorrector qc = holder.get();
        CorrectionResources res = qc.resources();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", holder.props().getSource());
        out.put("loadedAt", holder.loadedAt());
        out.put("loadMillis", holder.loadMillis());
        LinearModel m = res.model();
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("version", m.version());
        model.put("mediumThreshold", m.mediumThreshold());
        model.put("highThreshold", m.highThreshold());
        Map<String, Double> w = new LinkedHashMap<>();
        for (int i = 0; i < Features.SIZE; i++) {
            w.put(Features.NAMES[i], Math.round(m.weight(i) * 1000) / 1000.0);
        }
        model.put("weights", w);
        out.put("model", model);
        out.put("errorDictionaryEntries", res.errorDictionary().size());
        List<Map<String, Object>> countries = new ArrayList<>();
        Map<Integer, SampleWorld.Country> known = new LinkedHashMap<>();
        SampleWorld.countries().forEach(c -> known.put(c.id(), c));
        for (CountryResources cr : res.countries().values()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("countryId", cr.countryId());
            SampleWorld.Country k = known.get(cr.countryId());
            c.put("code", k == null ? String.valueOf(cr.countryId()) : k.code());
            c.put("name", k == null ? String.valueOf(cr.countryId()) : k.name());
            c.put("cities", k == null ? List.of() : k.cities());
            c.put("tokens", cr.lexicon().size());
            c.put("trieNodes", cr.lexicon().trie().size());
            c.put("bigramPairs", cr.bigram().pairCount());
            countries.add(c);
        }
        countries.sort((a, b) -> ((String) a.get("code")).compareTo((String) b.get("code")));
        out.put("countries", countries);
        return out;
    }

    @PostMapping("/admin/reload")
    public Map<String, Object> reload() throws IOException {
        holder.reload();
        return Map.of("reloaded", true, "loadMillis", holder.loadMillis());
    }
}
