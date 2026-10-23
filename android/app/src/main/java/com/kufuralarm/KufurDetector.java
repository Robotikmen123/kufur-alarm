package com.kufuralarm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Türkçe küfür tespiti. "ı" ile "i" ayrı tutulur ("sık" küfür sayılmaz). */
public final class KufurDetector {
    private static final Locale TR = new Locale("tr", "TR");

    private static final String[] PATTERNS = {
        "am[kq]", "aq", "amq", "mk",
        "amına\\w*", "amını", "amcık\\w*", "amınako\\w*",
        "orospu\\w*", "oç", "o\\.ç", "piç\\w*",
        "(?!sikke)sik\\w*", "hassik\\w*", "siktir\\w*", "s\\.ktir\\w*",
        "yarr?ak\\w*", "dalyarak\\w*", "yavşak\\w*",
        "göt", "götü", "götün\\w*", "götver\\w*", "götlek\\w*", "göte", "götten",
        "ibne\\w*", "pezevenk\\w*", "kahpe\\w*", "gavat\\w*", "puşt\\w*",
        "şerefsiz\\w*", "namussuz\\w*", "kaltak\\w*", "sürtük\\w*",
        "bok", "boku", "boktan", "bokum\\w*", "bokun\\w*",
        "amk\\w*", "aminako\\w*", "amina\\w*"
    };

    /** Harf sınıfı: Android'de de Türkçe harfleri kapsar (ç, ğ, ı, ö, ş, ü). */
    private static final String L = "[\\p{L}\\p{N}_]";
    private static final Pattern RE;
    private static final Pattern CENSORED = Pattern.compile("\\S*\\*+\\S*");

    static {
        StringBuilder sb = new StringBuilder("(?<!\\w)(?:");
        for (int i = 0; i < PATTERNS.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(PATTERNS[i]);
        }
        sb.append(")(?!\\w)");
        RE = Pattern.compile(sb.toString().replace("\\w", L));
    }

    private KufurDetector() {}

    public static List<String> find(String text) {
        List<String> hits = new ArrayList<>();
        if (text == null) return hits;
        String t = text.toLowerCase(TR);
        Matcher m = RE.matcher(t);
        while (m.find()) hits.add(m.group());
        // Google bazen küfrü "s***" diye sansürler: bunu da küfür say.
        Matcher c = CENSORED.matcher(t);
        while (c.find()) hits.add(c.group());
        return hits;
    }
}
