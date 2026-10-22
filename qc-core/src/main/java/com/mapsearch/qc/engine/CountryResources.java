package com.mapsearch.qc.engine;

import com.mapsearch.qc.dict.Lexicon;
import com.mapsearch.qc.lm.BigramModel;

/** Everything the engine needs for one country; swapped atomically on refresh. */
public record CountryResources(int countryId, Lexicon lexicon, BigramModel bigram) {
}
