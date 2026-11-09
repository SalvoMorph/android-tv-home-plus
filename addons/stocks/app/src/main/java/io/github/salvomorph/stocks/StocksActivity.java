package io.github.salvomorph.stocks;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Markets on the TV: four pages (indices, commodities and currencies, US stocks, Italian stocks), Left and Right
 * to change page, OK to refresh. Built in code, no layout files, no libraries.
 *
 * Quiet by design: it reads Yahoo Finance only while this page is on screen, the visible page's instruments only,
 * at most every two minutes; nothing runs in the background and the last readings are kept so the page is never
 * empty on a slow network.
 */
public final class StocksActivity extends Activity {
    private static final long STALE_MS = 2 * 60_000L;
    private static final int UP = 0xFF34C759;
    private static final int DOWN = 0xFFFF453A;
    private static final int FLAT = 0xFF8E8E93;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Quote> quotes = new HashMap<>();
    private final Map<String, Long> fetchedAt = new HashMap<>();
    private SharedPreferences cache;
    private ExecutorService io;
    private int page;
    private int pending;
    private boolean failed;
    private long updatedAt;

    private LinearLayout tabs;
    private LinearLayout list;
    private TextView status;

    private final Runnable tick = () -> refresh(false);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cache = getSharedPreferences("quotes", MODE_PRIVATE);
        page = state == null ? 0 : Math.max(0, Math.min(Catalog.PAGES.length - 1, state.getInt("page", 0)));
        setContentView(buildUi());
        loadCache();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("page", page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        refresh(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(tick);
        if (io != null) {
            io.shutdownNow();
            io = null;
        }
        pending = 0;
        saveCache();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (page > 0) showPage(page - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (page < Catalog.PAGES.length - 1) showPage(page + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                refresh(true);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;   // nothing to move between: keep focus from wandering
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void showPage(int next) {
        page = next;
        render();
        refresh(false);
    }

    // ---- data ---------------------------------------------------------------------------------------

    /** Reads the visible page's instruments that are older than two minutes (all of them when forced). */
    private void refresh(boolean force) {
        main.removeCallbacks(tick);
        main.postDelayed(tick, STALE_MS);
        if (pending > 0) return;
        long now = System.currentTimeMillis();
        Catalog.Item[] items = Catalog.PAGES[page];
        java.util.List<Catalog.Item> due = new java.util.ArrayList<>();
        for (Catalog.Item item : items) {
            Long at = fetchedAt.get(item.symbol);
            if (force || at == null || now - at >= STALE_MS || now < at) due.add(item);
        }
        if (due.isEmpty()) return;
        if (io == null) {
            io = Executors.newFixedThreadPool(3, r -> {
                Thread t = new Thread(r, "quotes");
                t.setDaemon(true);
                return t;
            });
        }
        final ExecutorService mine = io;
        pending = due.size();
        failed = false;
        render();
        for (Catalog.Item item : due) {
            mine.execute(() -> {
                Quote q = null;
                try {
                    q = YahooClient.fetch(item.symbol);
                } catch (Exception e) {
                    android.util.Log.w("Markets", item.symbol + ": " + e);
                }
                final Quote result = q;
                main.post(() -> {
                    if (io != mine) return;
                    if (result != null) {
                        quotes.put(item.symbol, result);
                        fetchedAt.put(item.symbol, System.currentTimeMillis());
                        updatedAt = System.currentTimeMillis();
                    } else {
                        failed = true;
                    }
                    pending--;
                    render();
                });
            });
        }
    }

    private void saveCache() {
        SharedPreferences.Editor e = cache.edit();
        for (Map.Entry<String, Quote> entry : quotes.entrySet()) {
            Quote q = entry.getValue();
            e.putString(entry.getKey(), q.price + "|" + q.previous + "|" + q.currency + "|" + q.time);
        }
        e.putLong("updated", updatedAt).apply();
    }

    private void loadCache() {
        updatedAt = cache.getLong("updated", 0);
        for (Catalog.Item[] items : Catalog.PAGES) {
            for (Catalog.Item item : items) {
                String s = cache.getString(item.symbol, null);
                if (s == null) continue;
                try {
                    String[] p = s.split("\\|", 4);
                    quotes.put(item.symbol, new Quote(item.symbol, Double.parseDouble(p[0]),
                            Double.parseDouble(p[1]), p[2], Long.parseLong(p[3])));
                } catch (RuntimeException ignored) {
                    // a damaged entry is just a missing reading
                }
            }
        }
    }

    // ---- screen -------------------------------------------------------------------------------------

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0A0B0E);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(56), dp(30), dp(56), dp(20));
        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = text(getString(R.string.app_name), 34, 0xFFFFFFFF, Typeface.create("sans-serif-light", Typeface.NORMAL));
        column.addView(title);

        tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tabsLp.topMargin = dp(14);
        column.addView(tabs, tabsLp);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listLp.topMargin = dp(14);
        column.addView(list, listLp);

        status = text("", 13, 0x99FFFFFF, Typeface.DEFAULT);
        status.setLineSpacing(dp(2), 1f);
        column.addView(status);
        return root;
    }

    private void render() {
        // tabs
        tabs.removeAllViews();
        for (int i = 0; i < Catalog.PAGES.length; i++) {
            boolean on = i == page;
            TextView tab = text(getString(Catalog.PAGE_TITLES[i]), 17, on ? 0xFFFFFFFF : 0x99FFFFFF,
                    on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tab.setPadding(dp(16), dp(7), dp(16), dp(7));
            tab.setBackground(pill(on ? 0x33FFFFFF : 0x14FFFFFF, 18));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(10);
            tabs.addView(tab, lp);
        }
        // rows
        list.removeAllViews();
        Catalog.Item[] items = Catalog.PAGES[page];
        for (Catalog.Item item : items) list.addView(row(item), rowParams());
        // status line
        StringBuilder s = new StringBuilder();
        if (pending > 0) s.append(getString(R.string.updating));
        else if (updatedAt > 0) s.append(getString(R.string.updated_at, DateFormat.getTimeFormat(this).format(new Date(updatedAt))));
        else s.append(getString(R.string.no_data));
        if (failed && pending == 0) s.append("  ·  ").append(getString(R.string.some_failed));
        s.append("\n").append(getString(R.string.footer));
        status.setText(s);
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.bottomMargin = dp(6);
        return lp;
    }

    private View row(Catalog.Item item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(22), 0, dp(22), 0);
        row.setBackground(pill(0x1AFFFFFF, 14));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(text(item.name, 22, 0xFFFFFFFF, Typeface.DEFAULT));
        names.addView(text(item.symbol, 12, 0x80FFFFFF, Typeface.DEFAULT));
        row.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Quote q = quotes.get(item.symbol);
        TextView price = text(q == null ? "—" : formatPrice(item, q), 24, 0xFFFFFFFF, Typeface.DEFAULT_BOLD);
        price.setGravity(Gravity.END);
        row.addView(price, new LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView change = text(q == null || !q.hasChange() ? "—" : formatPercent(q.percent()), 22,
                q == null || !q.hasChange() ? FLAT : colorOf(q.percent()), Typeface.DEFAULT_BOLD);
        change.setGravity(Gravity.END);
        row.addView(change, new LinearLayout.LayoutParams(dp(130), ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    // ---- formatting ---------------------------------------------------------------------------------

    static int colorOf(double percent) {
        if (Math.abs(percent) < 0.005) return FLAT;
        return percent > 0 ? UP : DOWN;
    }

    static String formatPercent(double percent) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.getDefault());
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        String sign = percent > 0.005 ? "+" : percent < -0.005 ? "−" : "";
        return sign + f.format(Math.abs(percent)) + "%";
    }

    private static String formatPrice(Catalog.Item item, Quote q) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.getDefault());
        int digits = item.kind == Catalog.Kind.RATE ? 4 : (q.price >= 1 ? 2 : 4);
        f.setMinimumFractionDigits(digits);
        f.setMaximumFractionDigits(digits);
        String number = f.format(q.price);
        if (item.kind != Catalog.Kind.PRICE) return number;
        return number + " " + symbolOf(q.currency);
    }

    private static String symbolOf(String code) {
        if (code == null || code.isEmpty()) return "";
        try {
            // "USD" would print as "US$" in some locales; the short signs read better on a TV.
            switch (code) {
                case "USD": return "$";
                case "EUR": return "€";
                case "GBP": return "£";
                case "JPY": return "¥";
                default: return Currency.getInstance(code).getSymbol(Locale.getDefault());
            }
        } catch (IllegalArgumentException e) {
            return code;
        }
    }

    // ---- small helpers ------------------------------------------------------------------------------

    private TextView text(String s, float sp, int color, Typeface face) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(face);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        return t;
    }

    private GradientDrawable pill(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
