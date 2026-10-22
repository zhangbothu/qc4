package com.mapsearch.qc.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CorrectionControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void correctsPartialInputWithoutCompleting() throws Exception {
        mvc.perform(get("/api/v1/correct").param("q", "Starbucls coff").param("country", "484"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corrected").value("Starbucks coff"))
                .andExpect(jsonPath("$.changed").value(true));
    }

    @Test
    void usesContextForMultiTokenQueries() throws Exception {
        mvc.perform(post("/api/v1/correct").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"The Toeer of London\",\"countryId\":826}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corrected").value("The Tower of London"));
    }

    @Test
    void batchAndValidation() throws Exception {
        mvc.perform(post("/api/v1/correct/batch").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"query\":\"aple\",\"countryId\":458},{\"query\":\"Ecatepec de Moaalos\",\"countryId\":484}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].corrected").value("appl"))
                .andExpect(jsonPath("$[1].corrected").value("Ecatepec de Morelos"));

        mvc.perform(post("/api/v1/correct").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownCountryPassesThrough() throws Exception {
        mvc.perform(get("/api/v1/correct").param("q", "starbucls").param("country", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corrected").value("starbucls"))
                .andExpect(jsonPath("$.changed").value(false));
    }

    @Test
    void metaListsCountries() throws Exception {
        mvc.perform(get("/api/v1/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countries").isArray());
    }
}
