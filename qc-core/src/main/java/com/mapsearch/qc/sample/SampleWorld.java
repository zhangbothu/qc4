package com.mapsearch.qc.sample;

import com.mapsearch.qc.channel.PrefixAlign;
import com.mapsearch.qc.click.ClickRecord;
import com.mapsearch.qc.corpus.PoiRecord;
import com.mapsearch.qc.text.CasePattern;
import com.mapsearch.qc.text.Keyboard;
import com.mapsearch.qc.text.Phonetic;
import com.mapsearch.qc.text.TextNormalizer;
import com.mapsearch.qc.train.LabeledQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Deterministic synthetic world: POIs for MX / GB / MY / ID with Zipf-like popularity, and user queries typed
 * with realistic noise (keyboard slips, phonetic confusions, dropped doubled letters, transpositions) at
 * random points of an unfinished sug input.
 *
 * <p>This only exists so that the whole pipeline (dictionary build, click mining, training, evaluation, service)
 * runs without access to the production ES indexes and query logs. Metrics measured on it are not a
 * substitute for metrics on real traffic.</p>
 */
public final class SampleWorld {

    public static final int MX = SampleCatalog.MX;
    public static final int GB = SampleCatalog.GB;
    public static final int MY = SampleCatalog.MY;
    public static final int ID = SampleCatalog.ID;

    public record Country(int id, String code, String name, List<CityInfo> cities) {
    }

    public record CityInfo(int id, String name) {
    }

    private record Target(String text, String googleName, String googleAddress) {
    }

    private final List<PoiRecord> pois = new ArrayList<>();
    private final List<Double> popularity = new ArrayList<>();
    /** What users (and Google) call the POI; differs from the stored name when the POI database has a typo. */
    private final List<String> intendedNames = new ArrayList<>();
    private final List<double[]> cumulativeByCountry = new ArrayList<>();
    private final List<int[]> rangeByCountry = new ArrayList<>();

    private SampleWorld() {
    }

    public static List<Country> countries() {
        List<Country> out = new ArrayList<>();
        for (SampleCatalog.Spec s : SampleCatalog.ALL) {
            out.add(new Country(s.countryId(), s.code(), s.countryName(),
                    s.cities().stream().map(c -> new CityInfo(c.id(), c.name())).toList()));
        }
        return out;
    }

    public List<PoiRecord> pois() {
        return pois;
    }

    /** Random language-flavoured tokens to inflate a dictionary to production size (latency benchmarks). */
    public static List<String> fillerVocabulary(int countryId, int count, long seed) {
        for (SampleCatalog.Spec s : SampleCatalog.ALL) {
            if (s.countryId() == countryId) {
                return PseudoWords.generate(s, count, new Random(seed));
            }
        }
        return List.of();
    }

    public static SampleWorld generate(int poisPerCountry, long seed) {
        SampleWorld w = new SampleWorld();
        Random r = new Random(seed);
        long id = 1;
        for (SampleCatalog.Spec spec : SampleCatalog.ALL) {
            int from = w.pois.size();
            for (int k = 0; k < spec.landmarks().size(); k++) {
                SampleCatalog.City city = spec.cities().get(k % 3 == 0 ? 0 : k % spec.cities().size());
                if (spec.landmarks().get(k).contains("Ecatepec")) {
                    city = spec.cities().get(1);
                }
                w.add(new PoiRecord(id++, spec.countryId(), city.id(), spec.landmarks().get(k),
                        address(spec, city, r), false), 400.0 / (1 + k * 0.25));
            }
            for (SampleCatalog.City city : spec.cities()) {
                w.add(new PoiRecord(id++, spec.countryId(), city.id(), city.name(), spec.countryName(), false), 150);
            }
            List<String> tail = PseudoWords.generate(spec, 3000, r);
            double[] tailCum = zipfCumulative(tail.size(), 1.05);
            while (w.pois.size() - from < poisPerCountry) {
                SampleCatalog.City city = pickCity(spec, r);
                double[] pop = new double[1];
                String name = r.nextDouble() < 0.3
                        ? PseudoWords.businessName(spec, tail.get(pickCum(tailCum, r)), tail.get(pickCum(tailCum, r)), r)
                        : name(spec, r, pop);
                if (pop[0] == 0) {
                    pop[0] = 1 + r.nextDouble();
                }
                String intended = name;
                if (r.nextDouble() < 0.015) {
                    name = misspellOneToken(name, r);
                }
                boolean postcode = r.nextDouble() < 0.01;
                String stored = postcode ? postcodeName(spec, r) : name;
                w.add(new PoiRecord(id++, spec.countryId(), city.id(), stored, address(spec, city, r), postcode),
                        pop[0], postcode ? stored : intended);
            }
            w.rangeByCountry.add(new int[]{spec.countryId(), from, w.pois.size()});
        }
        for (int[] range : w.rangeByCountry) {
            double[] cum = new double[range[2] - range[1]];
            double acc = 0;
            for (int i = range[1]; i < range[2]; i++) {
                acc += Math.pow(w.popularity.get(i), 0.8);
                cum[i - range[1]] = acc;
            }
            w.cumulativeByCountry.add(cum);
        }
        return w;
    }

    private void add(PoiRecord p, double pop) {
        add(p, pop, p.name());
    }

    private void add(PoiRecord p, double pop, String intendedName) {
        pois.add(p);
        popularity.add(pop);
        intendedNames.add(intendedName);
    }

    private static <T> T pick(List<T> l, Random r) {
        return l.get(r.nextInt(l.size()));
    }

    /** Zipf-ish pick: early entries of the list are more popular. */
    private static int zipf(int n, Random r, double s) {
        double u = r.nextDouble();
        double norm = 0;
        for (int i = 1; i <= n; i++) {
            norm += 1 / Math.pow(i, s);
        }
        double acc = 0;
        for (int i = 1; i <= n; i++) {
            acc += 1 / Math.pow(i, s) / norm;
            if (u <= acc) {
                return i - 1;
            }
        }
        return n - 1;
    }

    private static double[] zipfCumulative(int n, double s) {
        double[] cum = new double[n];
        double acc = 0;
        for (int i = 0; i < n; i++) {
            acc += 1 / Math.pow(i + 1, s);
            cum[i] = acc;
        }
        return cum;
    }

    private static int pickCum(double[] cum, Random r) {
        double u = r.nextDouble() * cum[cum.length - 1];
        int lo = 0;
        int hi = cum.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cum[mid] < u) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** Real POI databases contain typos too; they end up in the token dictionary with low frequency. */
    private static String misspellOneToken(String name, Random r) {
        String[] parts = name.split(" ");
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].length() >= 5 && TextNormalizer.isAsciiLetters(parts[i].toLowerCase(Locale.ROOT))) {
                idx.add(i);
            }
        }
        if (idx.isEmpty()) {
            return name;
        }
        int i = idx.get(r.nextInt(idx.size()));
        parts[i] = CasePattern.apply(parts[i], noise(parts[i].toLowerCase(Locale.ROOT), r));
        return String.join(" ", parts);
    }

    private static SampleCatalog.City pickCity(SampleCatalog.Spec spec, Random r) {
        return spec.cities().get(zipf(spec.cities().size(), r, 0.9));
    }

    private static String name(SampleCatalog.Spec spec, Random r, double[] pop) {
        double u = r.nextDouble();
        pop[0] = 1 + r.nextDouble() * 2;
        int b = zipf(spec.brands().size(), r, 0.8);
        String brand = spec.brands().get(b);
        switch (spec.countryId()) {
            case SampleCatalog.MX -> {
                if (u < 0.24) {
                    pop[0] = 3 + 20.0 / (1 + b);
                    return r.nextDouble() < 0.35 ? brand + " " + pick(spec.areas(), r) : brand;
                } else if (u < 0.44) {
                    return pick(spec.categories(), r) + " " + pick(spec.nicknames(), r);
                } else if (u < 0.52) {
                    return pick(spec.categories(), r) + " " + pick(spec.areas(), r);
                } else if (u < 0.58) {
                    return (r.nextBoolean() ? "Escuela Primaria " : "Jardin de Ninos ") + pick(spec.persons(), r);
                } else if (u < 0.63) {
                    return (r.nextBoolean() ? "Iglesia de " : "Parroquia de ") + pick(spec.saints(), r);
                } else if (u < 0.71) {
                    return (r.nextBoolean() ? "Calle " : "Avenida ") + pick(spec.streets(), r);
                } else if (u < 0.75) {
                    return "Colonia " + pick(spec.areas(), r);
                } else if (u < 0.79) {
                    return "Mercado " + pick(spec.areas(), r);
                } else if (u < 0.86) {
                    return pick(spec.categories(), r) + " " + pick(spec.persons(), r);
                } else {
                    return "Plaza " + pick(spec.areas(), r);
                }
            }
            case SampleCatalog.GB -> {
                List<String> streetTypes = spec.persons();
                if (u < 0.26) {
                    pop[0] = 3 + 20.0 / (1 + b);
                    return r.nextDouble() < 0.3 ? brand + " " + pick(spec.areas(), r) : brand;
                } else if (u < 0.34) {
                    return pick(spec.nicknames(), r);
                } else if (u < 0.46) {
                    return pick(spec.streets(), r) + " " + pick(spec.categories(), r);
                } else if (u < 0.58) {
                    return pick(spec.areas(), r) + " " + pick(spec.categories(), r);
                } else if (u < 0.66) {
                    return pick(spec.saints(), r) + (r.nextBoolean() ? " Church" : " Primary School");
                } else {
                    return pick(spec.streets(), r) + " " + pick(streetTypes, r);
                }
            }
            case SampleCatalog.MY -> {
                if (u < 0.24) {
                    pop[0] = 3 + 20.0 / (1 + b);
                    return r.nextDouble() < 0.35 ? brand + " " + pick(spec.areas(), r) : brand;
                } else if (u < 0.44) {
                    return pick(spec.categories(), r) + " " + pick(spec.nicknames(), r);
                } else if (u < 0.52) {
                    return pick(spec.categories(), r) + " " + pick(spec.areas(), r);
                } else if (u < 0.60) {
                    return pick(spec.saints(), r) + " " + pick(spec.nicknames(), r);
                } else if (u < 0.74) {
                    return "Jalan " + pick(spec.streets(), r);
                } else if (u < 0.82) {
                    return pick(spec.areas(), r);
                } else if (u < 0.90) {
                    return (r.nextBoolean() ? "Sekolah Kebangsaan " : "Sekolah Menengah Kebangsaan ")
                            + pick(spec.areas(), r);
                } else {
                    return "Jalan " + pick(spec.persons(), r);
                }
            }
            default -> {
                if (u < 0.24) {
                    pop[0] = 3 + 20.0 / (1 + b);
                    return r.nextDouble() < 0.35 ? brand + " " + pick(spec.areas(), r) : brand;
                } else if (u < 0.48) {
                    return pick(spec.categories(), r) + " " + pick(spec.nicknames(), r);
                } else if (u < 0.56) {
                    return pick(spec.categories(), r) + " " + pick(spec.areas(), r);
                } else if (u < 0.64) {
                    return pick(spec.saints(), r) + " " + pick(spec.nicknames(), r);
                } else if (u < 0.76) {
                    return "Jalan " + pick(spec.streets(), r);
                } else if (u < 0.86) {
                    return (r.nextBoolean() ? "Sekolah Dasar Negeri " : "Sekolah Menengah Pertama Negeri ")
                            + (1 + r.nextInt(30)) + " " + pick(spec.areas(), r);
                } else {
                    return pick(spec.categories(), r) + " " + pick(spec.persons(), r);
                }
            }
        }
    }

    private static String postcodeName(SampleCatalog.Spec spec, Random r) {
        return String.format(Locale.ROOT, "%05d", r.nextInt(99999));
    }

    private static String address(SampleCatalog.Spec spec, SampleCatalog.City city, Random r) {
        int num = 1 + r.nextInt(400);
        return switch (spec.countryId()) {
            case SampleCatalog.MX -> (r.nextBoolean() ? "Calle " : "Avenida ") + pick(spec.streets(), r) + " " + num
                    + ", Colonia " + pick(spec.areas(), r) + ", " + city.name();
            case SampleCatalog.GB -> num + " " + pick(spec.streets(), r) + " " + pick(spec.persons(), r) + ", "
                    + pick(spec.areas(), r) + ", " + city.name() + " " + (char) ('A' + r.nextInt(26))
                    + (char) ('A' + r.nextInt(26)) + (1 + r.nextInt(20)) + " " + r.nextInt(10)
                    + (char) ('A' + r.nextInt(26)) + (char) ('A' + r.nextInt(26));
            case SampleCatalog.MY -> num + ", Jalan " + pick(spec.streets(), r) + ", " + pick(spec.areas(), r) + ", "
                    + (50000 + r.nextInt(40000)) + " " + city.name();
            default -> "Jalan " + pick(spec.streets(), r) + " No " + num + ", " + pick(spec.areas(), r) + ", "
                    + city.name();
        };
    }

    // ---------------------------------------------------------------- queries

    private int samplePoi(int countryIdx, Random r) {
        double[] cum = cumulativeByCountry.get(countryIdx);
        double u = r.nextDouble() * cum[cum.length - 1];
        int lo = 0;
        int hi = cum.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cum[mid] < u) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return rangeByCountry.get(countryIdx)[1] + lo;
    }

    private Target target(int idx, Random r) {
        PoiRecord p = pois.get(idx);
        String name = intendedNames.get(idx);
        double u = r.nextDouble();
        String cityName = cityName(p.countryId(), p.cityId());
        if (u < 0.08 && !name.equals(cityName)) {
            return new Target(name + " " + cityName, name, p.address());
        }
        return new Target(name, name, p.address());
    }

    private static String cityName(int countryId, int cityId) {
        for (SampleCatalog.Spec s : SampleCatalog.ALL) {
            if (s.countryId() == countryId) {
                for (SampleCatalog.City c : s.cities()) {
                    if (c.id() == cityId) {
                        return c.name();
                    }
                }
            }
        }
        return "";
    }

    private record Typed(String query, String expected) {
    }

    /**
     * Simulates one sug input: the intended text, possibly cut while typing, with at most one misspelled token.
     */
    private Typed type(String intended, double errorRate, Random r) {
        String[] words = intended.trim().split("\\s+");
        double lower = r.nextDouble();
        for (int i = 0; i < words.length; i++) {
            if (lower < 0.6) {
                words[i] = words[i].toLowerCase(Locale.ROOT);
            }
        }
        int k = words.length - 1;
        int cut = -1;
        double mode = r.nextDouble();
        boolean trailingSpace = false;
        if (mode < 0.5) {
            k = r.nextInt(words.length);
            String w = words[k];
            cut = w.length() <= 3 ? w.length() : 3 + r.nextInt(w.length() - 2);
        } else if (mode < 0.85) {
            trailingSpace = false;
        } else {
            trailingSpace = true;
        }
        String[] typed = new String[k + 1];
        String[] gold = new String[k + 1];
        for (int i = 0; i <= k; i++) {
            String w = words[i];
            if (i == k && cut >= 0) {
                typed[i] = w.substring(0, Math.min(cut, w.length()));
            } else {
                typed[i] = w;
            }
            gold[i] = typed[i];
        }
        if (r.nextDouble() < errorRate) {
            List<Integer> eligible = new ArrayList<>();
            for (int i = 0; i <= k; i++) {
                String f = TextNormalizer.fold(words[i]);
                if (f.length() >= 4 && TextNormalizer.isAsciiLetters(f) && f.equals(words[i].toLowerCase(Locale.ROOT))) {
                    eligible.add(i);
                }
            }
            if (!eligible.isEmpty()) {
                int j = eligible.get(r.nextInt(eligible.size()));
                String intendedWord = words[j].toLowerCase(Locale.ROOT);
                for (int attempt = 0; attempt < 6; attempt++) {
                    String noisy = noise(intendedWord, r);
                    if (r.nextDouble() < 0.2 && intendedWord.length() >= 7) {
                        noisy = noise(noisy, r);
                    }
                    String typedTok;
                    String goldTok;
                    if (j == k && !trailingSpace) {
                        int t = cut >= 0 ? Math.min(Math.max(cut, 3), noisy.length()) : noisy.length();
                        typedTok = noisy.substring(0, t);
                        goldTok = PrefixAlign.alignedPrefix(typedTok, intendedWord);
                    } else {
                        typedTok = noisy;
                        goldTok = intendedWord;
                    }
                    if (!typedTok.equals(goldTok) && typedTok.length() >= 4) {
                        typed[j] = CasePattern.apply(words[j], typedTok);
                        gold[j] = CasePattern.apply(typed[j], goldTok);
                        break;
                    }
                }
            }
        }
        String q = String.join(" ", typed) + (trailingSpace ? " " : "");
        String e = String.join(" ", gold) + (trailingSpace ? " " : "");
        return new Typed(q, e);
    }

    static String noise(String w, Random r) {
        int n = w.length();
        int pos = r.nextDouble() < 0.05 ? 0 : 1 + r.nextInt(n - 1);
        char c = w.charAt(pos);
        double u = r.nextDouble();
        StringBuilder sb = new StringBuilder(w);
        if (u < 0.30) {
            sb.setCharAt(pos, neighbour(c, r));
        } else if (u < 0.50) {
            int dbl = -1;
            for (int i = 1; i < n; i++) {
                if (w.charAt(i) == w.charAt(i - 1)) {
                    dbl = i;
                }
            }
            sb.deleteCharAt(dbl >= 0 && r.nextBoolean() ? dbl : pos);
        } else if (u < 0.62) {
            sb.insert(pos, r.nextDouble() < 0.6 ? c : neighbour(c, r));
        } else if (u < 0.77) {
            int p = Math.min(pos, n - 2);
            if (w.charAt(p) != w.charAt(p + 1)) {
                sb.setCharAt(p, w.charAt(p + 1));
                sb.setCharAt(p + 1, w.charAt(p));
            } else {
                sb.setCharAt(pos, neighbour(c, r));
            }
        } else if (u < 0.92) {
            List<Character> alts = new ArrayList<>();
            for (char x = 'a'; x <= 'z'; x++) {
                if (Phonetic.phoneticPair(c, x)) {
                    alts.add(x);
                }
            }
            sb.setCharAt(pos, alts.isEmpty() ? neighbour(c, r) : alts.get(r.nextInt(alts.size())));
        } else {
            String vowels = "aeiou";
            if (Keyboard.isVowel(c)) {
                char v;
                do {
                    v = vowels.charAt(r.nextInt(5));
                } while (v == c);
                sb.setCharAt(pos, v);
            } else {
                sb.setCharAt(pos, neighbour(c, r));
            }
        }
        return sb.toString();
    }

    private static char neighbour(char c, Random r) {
        List<Character> n = new ArrayList<>();
        for (char x = 'a'; x <= 'z'; x++) {
            if (Keyboard.adjacent(c, x)) {
                n.add(x);
            }
        }
        return n.isEmpty() ? c : n.get(r.nextInt(n.size()));
    }

    /** Labeled sug inputs (ground truth known by construction). */
    public List<LabeledQuery> labeledQueries(int perCountry, double errorRate, long seed) {
        Random r = new Random(seed);
        List<LabeledQuery> out = new ArrayList<>();
        for (int ci = 0; ci < rangeByCountry.size(); ci++) {
            int country = rangeByCountry.get(ci)[0];
            for (int k = 0; k < perCountry; k++) {
                int idx = samplePoi(ci, r);
                PoiRecord p = pois.get(idx);
                Typed t = type(target(idx, r).text(), errorRate, r);
                int city = r.nextDouble() < 0.8 ? p.cityId() : 0;
                out.add(new LabeledQuery(country, city, t.query(), t.expected()));
            }
        }
        return out;
    }

    /**
     * Online logs: the typed query and the Google top-1 POI. A share of records are "semantic" matches where the
     * top-1 POI is unrelated to the typed text (the miner must not learn from those).
     */
    public List<ClickRecord> clickLogs(int perCountry, double errorRate, long seed) {
        Random r = new Random(seed);
        List<ClickRecord> out = new ArrayList<>();
        for (int ci = 0; ci < rangeByCountry.size(); ci++) {
            int country = rangeByCountry.get(ci)[0];
            for (int k = 0; k < perCountry; k++) {
                int idx = samplePoi(ci, r);
                PoiRecord p = pois.get(idx);
                Target tg = target(idx, r);
                Typed t = type(tg.text(), errorRate, r);
                String gName = tg.googleName();
                String gAddr = tg.googleAddress();
                if (r.nextDouble() < 0.06) {
                    PoiRecord other = pois.get(samplePoi(ci, r));
                    gName = other.name();
                    gAddr = other.address();
                }
                out.add(new ClickRecord(country, p.cityId(), "u" + r.nextInt(5000), t.query(), gName, gAddr));
            }
        }
        return out;
    }
}
