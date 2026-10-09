package com.blackfalcon.paknews;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int GREEN = 0xFF2ECC71, GRAY = 0xFF9E9E9E, CARD = 0xFF121212, EDGE = 0xFF2A2A2A;

    private SharedPreferences sp;
    private boolean urdu = false;
    private final Set<String> enabled = new HashSet<>();
    private final List<Channel> customs = new ArrayList<>();
    private final Map<String, String> customUrl = new HashMap<>();
    private final Map<String, List<Feeds.Item>> data = new HashMap<>();
    private final Map<String, String> status = new HashMap<>();
    private final List<Feeds.Item> visible = new ArrayList<>();
    private String filter = "all", query = "";
    private int generation = 0, finished = 0, total = 0;
    private long lastUpdated = 0;
    private final ExecutorService pool = Executors.newFixedThreadPool(6);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout root, chipRow;
    private TextView titleTv, statusTv, langBtn;
    private EditText search;
    private ListView list;
    private ItemAdapter adapter;

    private final Runnable autoRefresh = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 5 * 60 * 1000L); }
    };

    private String t(String en, String ur) { return urdu ? ur : en; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ------------------------------------------------------------- lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("news", MODE_PRIVATE);
        urdu = sp.getBoolean("urdu", false);
        loadCustoms();
        loadEnabled();
        for (Channel c : channels()) loadCache(c.id);
        buildUi();
        rebuildChips();
        rebuildList();
    }

    @Override protected void onResume() { super.onResume(); handler.post(autoRefresh); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(autoRefresh); }
    @Override protected void onDestroy() { super.onDestroy(); pool.shutdownNow(); }

    // ------------------------------------------------------------- channels
    private List<Channel> channels() {
        List<Channel> all = new ArrayList<>();
        Collections.addAll(all, Channel.ALL);
        all.addAll(customs);
        return all;
    }

    private List<Channel> enabledChannels() {
        List<Channel> out = new ArrayList<>();
        for (Channel c : channels()) if (enabled.contains(c.id)) out.add(c);
        return out;
    }

    private String nameOf(Channel c) { return urdu ? c.nameUr : c.nameEn; }

    private void loadCustoms() {
        customs.clear();
        customUrl.clear();
        String raw = sp.getString("customs", "");
        for (String line : raw.split("\n")) {
            String[] p = line.split("\t");
            if (p.length == 3) {
                Channel c = new Channel(p[0], p[1], p[1], 0xFF90CAF9, false, true, p[2]);
                customs.add(c);
                customUrl.put(p[0], p[2]);
            }
        }
    }

    private void saveCustoms() {
        StringBuilder sb = new StringBuilder();
        for (Channel c : customs) sb.append(c.id).append('\t').append(c.nameEn.replace('\t', ' ').replace('\n', ' ')).append('\t').append(customUrl.get(c.id)).append('\n');
        sp.edit().putString("customs", sb.toString()).apply();
    }

    private void loadEnabled() {
        enabled.clear();
        String raw = sp.getString("enabled", null);
        if (raw == null) {
            for (Channel c : channels()) if (c.defaultOn) enabled.add(c.id);
        } else {
            for (String s : raw.split(",")) if (!s.isEmpty()) enabled.add(s);
        }
    }

    private void saveEnabled() {
        StringBuilder sb = new StringBuilder();
        for (String s : enabled) sb.append(s).append(',');
        sp.edit().putString("enabled", sb.toString()).apply();
    }

    // ----------------------------------------------------------------- cache
    private void saveCache(String id, List<Feeds.Item> items) {
        StringBuilder sb = new StringBuilder();
        for (Feeds.Item it : items) {
            sb.append(it.ts).append('\t').append(it.title.replace('\t', ' ').replace('\n', ' ')).append('\t').append(it.link).append('\n');
        }
        sp.edit().putString("cache_" + id, sb.toString()).apply();
    }

    private void loadCache(String id) {
        String raw = sp.getString("cache_" + id, "");
        List<Feeds.Item> items = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String[] p = line.split("\t");
            if (p.length == 3) {
                Feeds.Item it = new Feeds.Item();
                it.channelId = id;
                try { it.ts = Long.parseLong(p[0]); } catch (NumberFormatException e) { it.ts = 0; }
                it.title = p[1];
                it.link = p[2];
                items.add(it);
            }
        }
        if (!items.isEmpty()) data.put(id, items);
    }

    // ---------------------------------------------------------------- loading
    private void refresh() {
        generation++;
        final int gen = generation;
        List<Channel> chs = enabledChannels();
        finished = 0;
        total = chs.size();
        for (Channel c : chs) status.put(c.id, "loading");
        updateStatus();
        for (final Channel c : chs) {
            pool.execute(() -> {
                List<Feeds.Item> items = null;
                try {
                    items = customUrl.containsKey(c.id) ? Feeds.loadUrl(customUrl.get(c.id), c.id, 40) : Feeds.load(c, 40);
                } catch (Exception ignored) { }
                final List<Feeds.Item> res = items;
                runOnUiThread(() -> {
                    if (gen != generation || isFinishing()) return;
                    finished++;
                    if (res != null && !res.isEmpty()) {
                        data.put(c.id, res);
                        status.put(c.id, "ok");
                        saveCache(c.id, res);
                        lastUpdated = System.currentTimeMillis();
                    } else {
                        status.put(c.id, "error");
                    }
                    rebuildList();
                });
            });
        }
        if (chs.isEmpty()) rebuildList();
    }

    // ------------------------------------------------------------------- UI
    private TextView tv(int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private GradientDrawable round(int fill, int stroke, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(1), stroke);
        g.setCornerRadius(dp(r));
        return g;
    }

    private TextView iconBtn(String s) {
        TextView b = tv(20, GREEN, true);
        b.setText(s);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        b.setClickable(true);
        return b;
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF0B0B0B);
        bar.setPadding(dp(14), dp(8), dp(6), dp(8));
        titleTv = tv(21, Color.WHITE, true);
        titleTv.setSingleLine(true);
        TextView refresh = iconBtn("\u21BB");
        refresh.setOnClickListener(v -> refresh());
        TextView gear = iconBtn("\u2699");
        gear.setOnClickListener(v -> channelsDialog());
        langBtn = tv(13, Color.BLACK, true);
        langBtn.setGravity(Gravity.CENTER);
        langBtn.setPadding(dp(12), dp(7), dp(12), dp(7));
        langBtn.setBackground(round(GREEN, GREEN, 16));
        langBtn.setClickable(true);
        langBtn.setOnClickListener(v -> {
            urdu = !urdu;
            sp.edit().putBoolean("urdu", urdu).apply();
            applyLanguage();
        });
        bar.addView(titleTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(refresh);
        bar.addView(gear);
        bar.addView(langBtn);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        chipRow = new LinearLayout(this);
        chipRow.setPadding(dp(8), dp(8), dp(8), dp(4));
        hs.addView(chipRow);

        search = new EditText(this);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xFF707070);
        search.setTextSize(15);
        search.setSingleLine(true);
        search.setPadding(dp(14), dp(8), dp(14), dp(8));
        search.setBackground(round(CARD, EDGE, 20));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { query = s.toString().trim(); rebuildList(); }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sl.setMargins(dp(12), dp(4), dp(12), dp(2));

        statusTv = tv(12, GRAY, false);
        statusTv.setPadding(dp(16), dp(4), dp(16), dp(4));

        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setPadding(dp(10), 0, dp(10), dp(6));
        list.setClipToPadding(false);
        adapter = new ItemAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> open(visible.get(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> { share(visible.get(pos)); return true; });

        TextView credit = tv(12, Color.WHITE, true);
        credit.setText("By: Black Falcon \uD83E\uDD85");
        credit.setGravity(Gravity.RIGHT);
        credit.setPadding(dp(14), dp(4), dp(14), dp(6));
        credit.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        root.addView(bar);
        root.addView(hs);
        root.addView(search, sl);
        root.addView(statusTv);
        root.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(credit);
        setContentView(root);
        applyTexts();
    }

    private void applyTexts() {
        root.setLayoutDirection(urdu ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        titleTv.setText(t("\uD83D\uDCF0 Pak News", "\uD83D\uDCF0 پاکستان خبریں"));
        langBtn.setText(urdu ? "English" : "اردو");
        search.setHint(t("Search headlines", "سرخیوں میں تلاش کریں"));
    }

    private void applyLanguage() {
        applyTexts();
        rebuildChips();
        rebuildList();
    }

    private void rebuildChips() {
        chipRow.removeAllViews();
        addChip("all", t("All", "سب"), GREEN);
        for (Channel c : enabledChannels()) addChip(c.id, nameOf(c), c.color);
    }

    private void addChip(final String id, String label, int color) {
        boolean on = id.equals(filter);
        TextView b = tv(13, on ? Color.BLACK : color, true);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        b.setBackground(round(on ? color : 0xFF101010, color, 18));
        b.setClickable(true);
        b.setOnClickListener(v -> { filter = id; rebuildChips(); rebuildList(); list.setSelection(0); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(3), 0, dp(3), 0);
        chipRow.addView(b, lp);
    }

    private void rebuildList() {
        visible.clear();
        String q = query.toLowerCase(Locale.getDefault());
        for (Channel c : enabledChannels()) {
            if (!filter.equals("all") && !filter.equals(c.id)) continue;
            List<Feeds.Item> items = data.get(c.id);
            if (items == null) continue;
            for (Feeds.Item it : items) {
                if (!q.isEmpty() && !it.title.toLowerCase(Locale.getDefault()).contains(q)) continue;
                visible.add(it);
            }
        }
        Collections.sort(visible, new Comparator<Feeds.Item>() {
            @Override public int compare(Feeds.Item a, Feeds.Item b) { return Long.compare(b.ts, a.ts); }
        });
        while (visible.size() > 400) visible.remove(visible.size() - 1);
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    private void updateStatus() {
        int ok = 0, err = 0, loading = 0;
        for (Channel c : enabledChannels()) {
            String s = status.get(c.id);
            if ("ok".equals(s)) ok++; else if ("error".equals(s)) err++; else if ("loading".equals(s)) loading++;
        }
        StringBuilder sb = new StringBuilder();
        if (loading > 0) sb.append(t("Loading… ", "لوڈ ہو رہا ہے… ")).append(finished).append("/").append(total).append("  ");
        if (lastUpdated > 0) sb.append(t("Updated ", "اپڈیٹ ")).append(new SimpleDateFormat("HH:mm", Locale.US).format(new Date(lastUpdated))).append("  ");
        if (err > 0) {
            StringBuilder bad = new StringBuilder();
            for (Channel c : enabledChannels()) if ("error".equals(status.get(c.id))) bad.append(bad.length() > 0 ? ", " : "").append(nameOf(c));
            sb.append(t("No connection for: ", "رابطہ نہیں ہوا: ")).append(bad).append(t(" (showing saved headlines)", " (محفوظ سرخیاں دکھائی جا رہی ہیں)"));
        } else if (loading == 0 && ok > 0) {
            sb.append(ok).append(t(" channels • ", " چینلز • ")).append(visible.size()).append(t(" headlines", " سرخیاں"));
        }
        if (sb.length() == 0) sb.append(visible.isEmpty() ? t("Pull the refresh button ↻ to load headlines.", "سرخیاں لانے کے لیے ↻ دبائیں۔") : "");
        statusTv.setText(sb.toString());
    }

    // ----------------------------------------------------------------- list
    private class ItemAdapter extends BaseAdapter {
        @Override public int getCount() { return visible.size(); }
        @Override public Object getItem(int i) { return visible.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout card;
            TextView name, time, title;
            View strip;
            if (convert == null) {
                card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setBackground(round(CARD, EDGE, 14));
                strip = new View(MainActivity.this);
                LinearLayout col = new LinearLayout(MainActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setPadding(dp(12), dp(10), dp(12), dp(10));
                LinearLayout top = new LinearLayout(MainActivity.this);
                name = tv(12, GREEN, true);
                time = tv(11, GRAY, false);
                time.setPadding(dp(8), 0, dp(8), 0);
                top.addView(name);
                top.addView(time);
                title = tv(16, Color.WHITE, false);
                title.setLineSpacing(0, 1.12f);
                title.setPadding(0, dp(4), 0, 0);
                title.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
                col.addView(top);
                col.addView(title);
                card.addView(strip, new LinearLayout.LayoutParams(dp(5), LinearLayout.LayoutParams.MATCH_PARENT));
                card.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                card.setTag(new View[]{strip, name, time, title});
                
                LinearLayout wrap = new LinearLayout(MainActivity.this);
                wrap.setPadding(0, dp(4), 0, dp(4));
                wrap.addView(card, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                wrap.setTag(card);
                convert = wrap;
            }
            card = (LinearLayout) convert.getTag();
            View[] v = (View[]) card.getTag();
            strip = v[0]; name = (TextView) v[1]; time = (TextView) v[2]; title = (TextView) v[3];
            Feeds.Item it = visible.get(pos);
            Channel ch = Channel.byId(it.channelId);
            if (ch == null) for (Channel c : customs) if (c.id.equals(it.channelId)) ch = c;
            int color = ch == null ? GREEN : ch.color;
            strip.setBackgroundColor(color);
            name.setTextColor(color);
            name.setText(ch == null ? "" : nameOf(ch));
            long now = System.currentTimeMillis();
            boolean fresh = now - it.ts < 30 * 60000L;
            time.setText("\u2022 " + Dates.ago(it.ts, now, urdu ? 1 : 0) + (fresh ? "  \uD83D\uDD34 " + t("NEW", "تازہ") : ""));
            title.setText(it.title);
            return convert;
        }
    }

    private void open(Feeds.Item it) {
        try {
            Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setBackgroundColor(CARD);

            // Top Bar
            LinearLayout topBar = new LinearLayout(this);
            topBar.setPadding(dp(16), dp(10), dp(16), dp(10));
            topBar.setBackgroundColor(0xFF0B0B0B);
            topBar.setGravity(Gravity.RIGHT);

            TextView closeBtn = tv(14, Color.RED, true);
            closeBtn.setText("❌ " + t("Close", "بند کریں"));
            closeBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
            closeBtn.setOnClickListener(v -> dialog.dismiss());

            topBar.addView(closeBtn);

            // WebView (In-App Browser)
            WebView webView = new WebView(this);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.setWebViewClient(new WebViewClient());
            webView.loadUrl(it.link);

            layout.addView(topBar);
            layout.addView(webView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            dialog.setContentView(layout);
            dialog.show();
        } catch (Exception e) {
            Toast.makeText(this, t("Could not open the link", "لنک نہیں کھل سکا"), Toast.LENGTH_SHORT).show();
        }
    }

    private void share(Feeds.Item it) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, it.title + "\n" + it.link);
        startActivity(Intent.createChooser(i, null));
    }

    // -------------------------------------------------------------- channels
    private void channelsDialog() {
        final List<Channel> all = channels();
        String[] names = new String[all.size()];
        final boolean[] on = new boolean[all.size()];
        for (int i = 0; i < names.length; i++) {
            Channel c = all.get(i);
            names[i] = nameOf(c) + (c.id.startsWith("custom") ? t("  (my link)", "  (میرا لنک)") : (c.urdu ? t("  (Urdu)", "  (اردو)") : ""));
            on[i] = enabled.contains(c.id);
        }
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(t("Choose channels", "چینلز منتخب کریں"))
                .setMultiChoiceItems(names, on, (dlg, which, checked) -> on[which] = checked)
                .setPositiveButton(t("Save", "محفوظ کریں"), (dlg, w) -> {
                    enabled.clear();
                    for (int i = 0; i < on.length; i++) if (on[i]) enabled.add(all.get(i).id);
                    saveEnabled();
                    if (!filter.equals("all") && !enabled.contains(filter)) filter = "all";
                    rebuildChips();
                    rebuildList();
                    refresh();
                })
                .setNeutralButton(t("＋ Add RSS link", "＋ آر ایس ایس لنک"), (dlg, w) -> addLinkDialog())
                .setNegativeButton(t("Cancel", "منسوخ"), null)
                .create();
        d.show();
        d.getListView().setLayoutDirection(urdu ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    }

    private void addLinkDialog() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText name = new EditText(this);
        name.setHint(t("Channel name", "چینل کا نام"));
        final EditText url = new EditText(this);
        url.setHint("https://…/feed");
        url.setSingleLine(true);
        name.setSingleLine(true);
        name.setTextColor(Color.WHITE);
        url.setTextColor(Color.WHITE);
        f.addView(name);
        f.addView(url);
        TextView hint = tv(12, GRAY, false);
        hint.setText(t("Paste the RSS/feed address of any news site.", "کسی بھی نیوز سائٹ کا آر ایس ایس (feed) ایڈریس پیسٹ کریں۔"));
        hint.setPadding(0, dp(8), 0, 0);
        f.addView(hint);
        new AlertDialog.Builder(this)
                .setTitle(t("Add my own link", "اپنا لنک شامل کریں"))
                .setView(f)
                .setPositiveButton(t("Add", "شامل کریں"), (dlg, w) -> {
                    String n = name.getText().toString().trim();
                    String u = url.getText().toString().trim();
                    if (n.isEmpty() || !u.startsWith("http")) {
                        Toast.makeText(this, t("Enter a name and a link starting with http", "نام اور http سے شروع ہونے والا لنک لکھیں"), Toast.LENGTH_LONG).show();
                        return;
                    }
                    String id = "custom" + System.currentTimeMillis();
                    customs.add(new Channel(id, n, n, 0xFF90CAF9, false, true, u));
                    customUrl.put(id, u);
                    enabled.add(id);
                    saveCustoms();
                    saveEnabled();
                    rebuildChips();
                    refresh();
                })
                .setNeutralButton(t("Remove my links", "میرے لنک ہٹائیں"), (dlg, w) -> {
                    for (Channel c : customs) { enabled.remove(c.id); data.remove(c.id); }
                    customs.clear();
                    customUrl.clear();
                    saveCustoms();
                    saveEnabled();
                    filter = "all";
                    rebuildChips();
                    rebuildList();
                })
                .setNegativeButton(t("Cancel", "منسوخ"), null)
                .show();
    }
}
