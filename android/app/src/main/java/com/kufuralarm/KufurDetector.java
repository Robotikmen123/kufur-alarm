package com.kufuralarm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Türkçe küfür tespiti. Liste PC sürümüyle (pc/kufur_alarm.py) aynıdır. */
public final class KufurDetector {
    private static final Locale TR = new Locale("tr", "TR");

    /** Asıl metin üzerinde. "ı" ile "i" ayrı: "sık" küfür sayılmaz. */
    private static final String[] PATTERNS = {
        "am[kq]\\w*", "aq", "a\\.q", "mk", "amına\\w*", "amını\\w*", "amcık\\w*",
        "amınako\\w*", "amınıza\\w*", "anan", "ananı\\w*", "ananın\\w*", "anana", "ananız\\w*",
        "bacın\\w*", "avradın\\w*", "sülaleni\\w*", "ecdadını\\w*", "ceddini\\w*",
        "orospu\\w*", "oruspu\\w*", "oç", "o\\.ç", "piç\\w*", "(?!sikke)sik\\w*", "hassik\\w*",
        "s\\.ktir\\w*", "sg", "yarrak\\w*", "yarak\\w*", "yarrağ\\w*", "yarağ\\w*",
        "yarram\\w*", "dalyarak\\w*", "yavşak\\w*", "taşak\\w*", "taşşak\\w*", "daşşak\\w*",
        "göt", "götü", "götün\\w*", "götüm\\w*", "götver\\w*", "götlek\\w*", "göte", "götten",
        "ibne\\w*", "pezevenk\\w*", "kahpe\\w*", "gavat\\w*", "puşt\\w*", "şerefsiz\\w*",
        "namussuz\\w*", "kaltak\\w*", "sürtük\\w*", "kancık\\w*", "bok", "boku", "boka",
        "boktan", "bokum\\w*", "bokun\\w*"
    };

    /** Sadeleştirilmiş metin (ı→i, ş→s...) üzerinde: yanlış yazımları da yakalar. */
    private static final String[] FOLDED_PATTERNS = {
        "amina\\w*", "amini\\w*", "aminako\\w*", "amcik\\w*", "amk\\w*", "anani\\w*",
        "ananin\\w*", "bacini\\w*", "bacinin\\w*", "avradini\\w*", "orospu\\w*", "oruspu\\w*",
        "oc", "pic", "pici\\w*", "picler\\w*", "siktir\\w*", "siktig\\w*", "sikeyim",
        "sikerim", "sikim\\w*", "sikik\\w*", "yarrak\\w*", "yarak\\w*", "yarag\\w*",
        "yavsak\\w*", "tasak\\w*", "dassak\\w*", "serefsiz\\w*", "pezevenk\\w*", "ibne\\w*",
        "gavat\\w*", "kahpe\\w*", "pust\\w*", "surtuk\\w*", "kancik\\w*", "gotun\\w*",
        "gotver\\w*"
    };

    /** Harf sınıfı: Android'de de Türkçe harfleri kapsar (ç, ğ, ı, ö, ş, ü). */
    private static final String L = "[\\p{L}\\p{N}_]";
    private static final Pattern CENSORED = Pattern.compile("\\S*\\*+\\S*");
    private static volatile Pattern re, foldedRe;

    static {
        setExtra("");
    }

    private KufurDetector() {}

    public static String fold(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case 'ı': c = 'i'; break;
                case 'ş': c = 's'; break;
                case 'ç': c = 'c'; break;
                case 'ğ': c = 'g'; break;
                case 'ö': c = 'o'; break;
                case 'ü': c = 'u'; break;
                case 'â': c = 'a'; break;
                case 'î': c = 'i'; break;
                case 'û': c = 'u'; break;
                default: break;
            }
            b.append(c);
        }
        return b.toString();
    }

    /** Kullanıcının eklediği kelimeler (her satıra bir tane). */
    public static void setExtra(String lines) {
        List<String> extra = new ArrayList<>();
        if (lines != null) {
            for (String w : lines.split("\n")) {
                w = w.trim().toLowerCase(TR);
                if (w.length() > 0) extra.add(Pattern.quote(w) + "\\w*");
            }
        }
        List<String> folded = new ArrayList<>();
        for (String w : extra) folded.add(fold(w));
        re = compile(PATTERNS, extra);
        foldedRe = compile(FOLDED_PATTERNS, folded);
    }

    private static Pattern compile(String[] base, List<String> extra) {
        StringBuilder sb = new StringBuilder("(?<!\\w)(?:");
        for (int i = 0; i < base.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(base[i]);
        }
        for (String e : extra) sb.append('|').append(e);
        sb.append(")(?!\\w)");
        return Pattern.compile(sb.toString().replace("\\w", L));
    }

    public static List<String> find(String text) {
        List<String> hits = new ArrayList<>();
        if (text == null) return hits;
        String t = text.toLowerCase(TR);
        Matcher m = re.matcher(t);
        while (m.find()) hits.add(m.group());
        if (hits.isEmpty()) {
            Matcher f = foldedRe.matcher(fold(t));
            while (f.find()) hits.add(f.group());
        }
        // Google bazen küfrü "s***" diye sansürler: bunu da küfür say.
        Matcher c = CENSORED.matcher(t);
        while (c.find()) hits.add(c.group());
        return hits;
    }
}
