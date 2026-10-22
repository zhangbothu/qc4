package com.mapsearch.qc.sample;

import com.mapsearch.qc.text.CasePattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Language-flavoured pseudo surnames / shop names. They give the synthetic dictionary the long tail of rare,
 * mutually similar tokens that real POI dictionaries have (and that makes correction hard).
 */
final class PseudoWords {

    private PseudoWords() {
    }

    private record Phonology(String[] onsets, String[] vowels, String[] codas, String[] suffixes) {
    }

    private static final Phonology ES = new Phonology(
            new String[]{"b", "c", "ch", "d", "f", "g", "gu", "j", "l", "ll", "m", "n", "p", "qu", "r", "s", "t", "v", "y", "z", ""},
            new String[]{"a", "e", "i", "o", "u", "ia", "ue"},
            new String[]{"", "", "", "n", "r", "s", "l"},
            new String[]{"ez", "es", "ado", "ero", "ino", "illa", "ales", "aga", "", ""});
    private static final Phonology EN = new Phonology(
            new String[]{"b", "bl", "br", "c", "ch", "cr", "d", "dr", "f", "fl", "g", "gr", "h", "j", "k", "l", "m", "n",
                    "p", "pr", "r", "s", "sh", "st", "t", "th", "tr", "w", "wh"},
            new String[]{"a", "e", "i", "o", "u", "ee", "oo", "ar", "or", "er", "ay", "ow"},
            new String[]{"", "", "n", "r", "l", "ck", "t", "m", "s"},
            new String[]{"son", "ton", "ley", "ford", "field", "wood", "well", "by", "ham", "worth", "ridge", "more", "shaw", ""});
    private static final Phonology MS = new Phonology(
            new String[]{"b", "c", "d", "g", "h", "j", "k", "l", "m", "n", "ng", "p", "r", "s", "t", "w", "y", "sy", "kh"},
            new String[]{"a", "i", "u", "e", "o", "ai", "au"},
            new String[]{"", "", "n", "ng", "h", "k", "r", "s", "t", "l", "m"},
            new String[]{"an", "kan", "nya", "din", "ah", "", ""});
    private static final Phonology IN = new Phonology(
            new String[]{"b", "c", "d", "g", "h", "j", "k", "l", "m", "n", "ny", "p", "r", "s", "t", "w", "y", "sy"},
            new String[]{"a", "i", "u", "e", "o", "ai", "au"},
            new String[]{"", "", "n", "ng", "h", "k", "r", "s", "t", "l", "m"},
            new String[]{"anto", "ono", "wati", "udin", "jaya", "sari", "an", ""});

    static List<String> generate(SampleCatalog.Spec spec, int count, Random r) {
        Phonology p = switch (spec.countryId()) {
            case SampleCatalog.MX -> ES;
            case SampleCatalog.GB -> EN;
            case SampleCatalog.MY -> MS;
            default -> IN;
        };
        Set<String> seen = new HashSet<>();
        List<String> out = new ArrayList<>();
        int guard = 0;
        while (out.size() < count && guard++ < count * 50) {
            StringBuilder sb = new StringBuilder();
            int syl = 1 + r.nextInt(3);
            for (int i = 0; i < syl; i++) {
                sb.append(p.onsets[r.nextInt(p.onsets.length)]).append(p.vowels[r.nextInt(p.vowels.length)]);
                if (r.nextDouble() < 0.4) {
                    sb.append(p.codas[r.nextInt(p.codas.length)]);
                }
            }
            sb.append(p.suffixes[r.nextInt(p.suffixes.length)]);
            String w = sb.toString();
            if (w.length() >= 4 && w.length() <= 12 && seen.add(w)) {
                out.add(w);
            }
        }
        return out;
    }

    static String businessName(SampleCatalog.Spec spec, String a, String b, Random r) {
        String x = CasePattern.apply("A", a);
        String y = CasePattern.apply("A", b);
        double u = r.nextDouble();
        return switch (spec.countryId()) {
            case SampleCatalog.MX -> u < 0.6 ? pick(spec.categories(), r) + " " + x
                    : u < 0.8 ? "Familia " + x + " " + y : "Grupo " + x;
            case SampleCatalog.GB -> u < 0.5 ? x + " " + pick(List.of("Solicitors", "Dental Practice", "and Sons",
                    "Estate Agents", "Garage", "Butchers", "Accountants", "Barbers", "Plumbing", "Florist"), r)
                    : u < 0.8 ? x + " " + y + " " + pick(List.of("Road", "Lane", "Close", "Way", "Court"), r)
                    : x + " " + pick(spec.categories(), r);
            case SampleCatalog.MY -> u < 0.6 ? pick(spec.categories(), r) + " " + x
                    : u < 0.8 ? "Kampung " + x : x + " " + y + " Enterprise";
            default -> u < 0.6 ? pick(List.of("Toko", "Warung", "Bengkel", "Apotek", "Salon", "Rumah Makan",
                    "Kedai", "Klinik"), r) + " " + x
                    : u < 0.8 ? "Gang " + x : x + " " + y;
        };
    }

    private static <T> T pick(List<T> l, Random r) {
        return l.get(r.nextInt(l.size()));
    }
}
