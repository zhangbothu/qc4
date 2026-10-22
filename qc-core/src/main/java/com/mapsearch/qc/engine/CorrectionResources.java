package com.mapsearch.qc.engine;

import com.mapsearch.qc.channel.ChannelModel;
import com.mapsearch.qc.click.ErrorDictionary;

import java.util.Map;

/** Immutable snapshot of all models / dictionaries. */
public record CorrectionResources(Map<Integer, CountryResources> countries,
                                  ChannelModel channel,
                                  ErrorDictionary errorDictionary,
                                  LinearModel model) {

    public CountryResources country(int countryId) {
        return countries.get(countryId);
    }

    public CorrectionResources withModel(LinearModel m) {
        return new CorrectionResources(countries, channel, errorDictionary, m);
    }

    public CorrectionResources withErrorDictionary(ErrorDictionary d) {
        return new CorrectionResources(countries, channel, d, model);
    }

    public CorrectionResources withChannel(ChannelModel c) {
        return new CorrectionResources(countries, c, errorDictionary, model);
    }
}
