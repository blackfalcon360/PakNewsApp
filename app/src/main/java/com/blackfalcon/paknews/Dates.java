package com.blackfalcon.paknews;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/** Date parsing for RSS (RFC 822) and Atom (ISO 8601), and "5 min ago" text. */
public final class Dates {
    private Dates() { }

    private static final String[] PATTERNS = {
            "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz", "dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss"
    };

    /** Returns epoch millis, or -1 when the text is not a recognisable date. */
    public static long parse(String raw) {
        if (raw == null) return -1;
        String s = raw.trim();
        if (s.isEmpty()) return -1;
        // ISO 8601 tidy-up: "Z" -> "+0000", "+05:00" -> "+0500"
        if (s.matches(".*\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$")) s = s.substring(0, s.length() - 1) + "+0000";
        s = s.replaceAll("([+-]\\d\\d):(\\d\\d)$", "$1$2");
        for (String p : PATTERNS) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                f.setLenient(true);
                if (!p.contains("Z") && !p.contains("z")) f.setTimeZone(TimeZone.getTimeZone("UTC"));
                return f.parse(s).getTime();
            } catch (ParseException ignored) { }
        }
        return -1;
    }

    /** lang: 0 English, 1 Urdu. */
    public static String ago(long ts, long now, int lang) {
        long m = Math.max(0, (now - ts) / 60000L);
        if (m < 1) return lang == 1 ? "ابھی" : "just now";
        if (m < 60) return lang == 1 ? m + " منٹ پہلے" : m + " min ago";
        long h = m / 60;
        if (h < 24) return lang == 1 ? h + " گھنٹے پہلے" : h + " h ago";
        long d = h / 24;
        return lang == 1 ? d + " دن پہلے" : d + " d ago";
    }

    /** Google News adds " - Source name" to every title; remove it. */
    public static String stripSource(String title) {
        int i = title.lastIndexOf(" - ");
        if (i > 12 && title.length() - i < 40) return title.substring(0, i).trim();
        return title;
    }
}
