package com.blackfalcon.paknews;

import android.os.Build;
import android.text.Html;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Downloads and reads RSS / Atom feeds. */
public final class Feeds {
    private Feeds() { }

    public static final class Item {
        public String channelId, title, link;
        public long ts;
    }

    private static String clean(String t) {
        if (t == null) return "";
        String s;
        if (Build.VERSION.SDK_INT >= 24) s = Html.fromHtml(t, Html.FROM_HTML_MODE_LEGACY).toString();
        else s = legacy(t);
        return s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    @SuppressWarnings("deprecation")
    private static String legacy(String t) { return Html.fromHtml(t).toString(); }

    /** Fetches one feed URL. Throws on network or parse errors. */
    public static List<Item> fetch(String urlStr, String channelId, int max) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) PakNews/1.0");
        c.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*");
        int code = c.getResponseCode();
        if (code / 100 != 2) { c.disconnect(); throw new Exception("HTTP " + code); }
        InputStream in = new BufferedInputStream(c.getInputStream());
        try {
            return parse(in, channelId, max);
        } finally {
            try { in.close(); } catch (Exception ignored) { }
            c.disconnect();
        }
    }

    static List<Item> parse(InputStream in, String channelId, int max) throws Exception {
        XmlPullParser xp = Xml.newPullParser();
        xp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        xp.setInput(in, null);
        List<Item> out = new ArrayList<>();
        boolean inItem = false;
        String title = "", link = "", date = "", guid = "";
        long base = System.currentTimeMillis();
        int ev = xp.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT && out.size() < max) {
            if (ev == XmlPullParser.START_TAG) {
                String n = xp.getName();
                if ("item".equals(n) || "entry".equals(n)) {
                    inItem = true; title = ""; link = ""; date = ""; guid = "";
                } else if (inItem) {
                    if ("title".equals(n)) {
                        title = xp.nextText();
                    } else if ("link".equals(n)) {
                        String href = xp.getAttributeValue(null, "href");
                        String rel = xp.getAttributeValue(null, "rel");
                        if (href != null) {
                            if (rel == null || "alternate".equals(rel)) link = href;
                            if (xp.next() == XmlPullParser.END_TAG) { ev = xp.getEventType(); ev = xp.next(); continue; }
                        } else {
                            link = xp.nextText();
                        }
                    } else if ("guid".equals(n) || "id".equals(n)) {
                        guid = xp.nextText();
                    } else if ("pubDate".equals(n) || "published".equals(n) || "updated".equals(n) || "dc:date".equals(n)) {
                        if (date.isEmpty()) date = xp.nextText();
                    }
                }
            } else if (ev == XmlPullParser.END_TAG) {
                String n = xp.getName();
                if (("item".equals(n) || "entry".equals(n)) && inItem) {
                    inItem = false;
                    String l = link == null ? "" : link.trim();
                    if (l.isEmpty() && guid != null && guid.trim().startsWith("http")) l = guid.trim();
                    String t = clean(title);
                    if (!t.isEmpty() && !l.isEmpty()) {
                        Item it = new Item();
                        it.channelId = channelId;
                        it.title = t;
                        it.link = l;
                        long ts = Dates.parse(date);
                        it.ts = ts > 0 ? Math.min(ts, base) : base - out.size() * 1000L; // keep feed order if no date
                        out.add(it);
                    }
                }
            }
            ev = xp.next();
        }
        return out;
    }

    /** Tries each address of the channel until one returns headlines. */
    public static List<Item> load(Channel ch, int max) throws Exception {
        Exception last = null;
        for (String u : ch.urls) {
            try {
                List<Item> items = fetch(u, ch.id, max);
                if (!items.isEmpty()) {
                    if (u.contains("news.google.com")) {
                        for (Item it : items) it.title = Dates.stripSource(it.title);
                    }
                    return items;
                }
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new Exception("no headlines");
    }

    /** Same, for a link the user typed in. */
    public static List<Item> loadUrl(String url, String id, int max) throws Exception {
        List<Item> items = fetch(url, id, max);
        if (url.contains("news.google.com")) for (Item it : items) it.title = Dates.stripSource(it.title);
        return items;
    }
}
