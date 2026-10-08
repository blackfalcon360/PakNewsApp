package com.blackfalcon.paknews;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/** A news channel: its own RSS feed when it has one, otherwise a Google News search of its website. */
public final class Channel {
    public final String id, nameEn, nameUr;
    public final int color;
    public final boolean urdu;
    public final boolean defaultOn;
    public final String[] urls; // tried in order until one gives headlines

    Channel(String id, String nameEn, String nameUr, int color, boolean urdu, boolean defaultOn, String... urls) {
        this.id = id; this.nameEn = nameEn; this.nameUr = nameUr; this.color = color;
        this.urdu = urdu; this.defaultOn = defaultOn; this.urls = urls;
    }

    /** Google News RSS for the latest stories from one website. */
    static String gn(String domain) {
        try {
            return "https://news.google.com/rss/search?q=" + URLEncoder.encode("site:" + domain + " when:3d", "UTF-8")
                    + "&hl=en-PK&gl=PK&ceid=PK:en";
        } catch (UnsupportedEncodingException e) {
            return "https://news.google.com/rss";
        }
    }

    public static final Channel[] ALL = {
            new Channel("geo", "Geo News", "جیو نیوز", 0xFFE53935, false, true, gn("geo.tv")),
            new Channel("ary", "ARY News", "اے آر وائی نیوز", 0xFFFB8C00, false, true, "https://arynews.tv/feed/", gn("arynews.tv")),
            new Channel("express", "Express News", "ایکسپریس نیوز", 0xFF1E88E5, true, true, "https://www.express.pk/feed/", gn("express.pk")),
            new Channel("dunya", "Dunya News", "دنیا نیوز", 0xFF8E24AA, false, true, gn("dunyanews.tv")),
            new Channel("samaa", "Samaa TV", "سماء ٹی وی", 0xFF00ACC1, false, true, gn("samaa.tv")),
            new Channel("dawn", "Dawn", "ڈان", 0xFF3949AB, false, true, "https://www.dawn.com/feeds/home", gn("dawn.com")),
            new Channel("tribune", "Express Tribune", "ایکسپریس ٹریبیون", 0xFF8D6E63, false, true, "https://tribune.com.pk/feed/home", gn("tribune.com.pk")),
            new Channel("n92", "92 News", "92 نیوز", 0xFF43A047, false, true, "https://92newshd.tv/feed", gn("92newshd.tv")),
            new Channel("aaj", "Aaj News", "آج نیوز", 0xFFF4511E, false, true, "https://www.aaj.tv/feeds/latest-news", gn("aaj.tv")),
            new Channel("bol", "BOL News", "بول نیوز", 0xFFFBC02D, false, true, "https://www.bolnews.com/feed/", gn("bolnews.com")),
            new Channel("hum", "Hum News", "ہم نیوز", 0xFFEC407A, false, true, gn("humnews.pk")),
            new Channel("neo", "Neo News", "نیو نیوز", 0xFF26A69A, false, true, gn("neonews.pk")),
            new Channel("n24", "24 News", "24 نیوز", 0xFF5C6BC0, false, false, gn("24newshd.tv"), "https://www.24urdu.com/rss/world"),
            new Channel("gnn", "GNN", "جی این این", 0xFF78909C, false, false, "https://gnnhd.tv/rss/latest", gn("gnnhd.tv")),
            new Channel("public", "Public News", "پبلک نیوز", 0xFF9CCC65, false, false, "https://publicnews.com/feed", gn("publicnews.tv")),
            new Channel("geourdu", "Geo Urdu", "جیو اردو", 0xFFEF5350, true, false, gn("urdu.geo.tv")),
            new Channel("dunyaurdu", "Dunya Urdu", "دنیا اردو", 0xFFBA68C8, true, false, gn("urdu.dunyanews.tv")),
            new Channel("n92urdu", "92 News Urdu", "92 نیوز اردو", 0xFF66BB6A, true, false, "https://urdu.92newshd.tv/feed", gn("urdu.92newshd.tv")),
            new Channel("bolurdu", "BOL Urdu", "بول اردو", 0xFFFFCA28, true, false, "https://www.bolnewsurdu.com/feed/", gn("bolnewsurdu.com")),
            new Channel("indurdu", "Independent Urdu", "انڈیپنڈنٹ اردو", 0xFF4DB6AC, true, false, "https://www.independenturdu.com/rss.xml"),
            new Channel("arabnews", "Arab News Pakistan", "عرب نیوز پاکستان", 0xFF29B6F6, false, false, "https://www.arabnews.pk/rss.xml"),
    };

    public static Channel byId(String id) {
        for (Channel c : ALL) if (c.id.equals(id)) return c;
        return null;
    }
}
