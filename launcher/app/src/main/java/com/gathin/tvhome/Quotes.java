package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A short motivational line under the clock, off unless the owner turns it on in Launcher settings.
 *
 * Two sources: a list built into the launcher (res/values/config_quotes.xml, in the TV's language where there is
 * one, no network at all), or ZenQuotes' "quote of the day" (free, no key, English, one request a day, shown with
 * its credit as the service asks). The online mode shows a quote from the list at once and swaps in the day's
 * quote when it arrives; if the network, the answer or its length does not suit, the list stays.
 *
 * Cost on the TV: nothing runs until {@link #start} (Home resumed) and nothing outlives {@link #stop}. A quote is
 * kept for 15 minutes, so coming back to Home does not flicker through them.
 */
final class Quotes {
    private static final String TAG = "TvHome.Quotes";
    static final String OFF = "off";
    static final String LOCAL = "local";
    static final String ONLINE = "online";
    /** The quote under the clock takes at most two lines; a longer one would be cut, so it is left out. */
    static final int MAX_LENGTH = 170;
    static final String CREDIT = "zenquotes.io";

    private static final String PREFS = "quotes_cache";
    private static final String URL_TODAY = "https://zenquotes.io/api/today";
    private static final long KEEP_MS = 15 * 60_000L;
    private static final long RETRY_AFTER_FAILURE_MS = 10 * 60_000L;
    private static final int TIMEOUT_MS = 8_000;
    private static final int MAX_BODY = 16 * 1024;

    // Process-wide, so a new Home activity (a theme push, a settings change) does not pick again at once.
    private static String shownText = "";
    private static String shownAuthor = "";
    private static String shownMode = "";
    private static long shownAt;
    private static int lastIndex = -1;
    private static final Random RANDOM = new Random();

    interface Listener {
        /** Always called on the main thread. {@code author} may be empty. */
        void onQuote(String text, String author);
    }

    private final Context ctx;
    private final String mode;
    private final Handler main;
    private final Listener listener;
    private final SharedPreferences prefs;
    /** Main thread only. */
    private ExecutorService io;
    private boolean started;

    Quotes(Context ctx, String mode, Handler main, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.mode = normalize(mode);
        this.main = main;
        this.listener = listener;
        this.prefs = this.ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String normalize(String mode) {
        if (mode == null) return OFF;
        String m = mode.trim().toLowerCase(java.util.Locale.ROOT);
        return LOCAL.equals(m) || ONLINE.equals(m) ? m : OFF;
    }

    void start() {
        if (started || OFF.equals(mode)) return;
        started = true;
        long now = SystemClock.elapsedRealtime();
        if (!shownText.isEmpty() && mode.equals(shownMode) && now - shownAt < KEEP_MS) {
            listener.onQuote(shownText, shownAuthor);
            return;
        }
        if (!ONLINE.equals(mode)) {
            deliverFromList();
            return;
        }
        String today = LocalDate.now().toString();
        String text = prefs.getString("text", "");
        if (today.equals(prefs.getString("day", "")) && !text.isEmpty()) {
            deliver(text, prefs.getString("author", ""));
            return;
        }
        deliverFromList();
        // Asked today already (even if the answer did not suit), or tried a moment ago: do not ask again.
        if (today.equals(prefs.getString("day", ""))) return;
        long sinceTry = System.currentTimeMillis() - prefs.getLong("tried", 0);
        if (sinceTry >= 0 && sinceTry < RETRY_AFTER_FAILURE_MS) return;
        fetch(today);
    }

    void stop() {
        started = false;
        if (io != null) {
            io.shutdownNow();
            io = null;
        }
    }

    private void deliverFromList() {
        String[] all = ctx.getResources().getStringArray(R.array.quotes_local);
        if (all.length == 0) return;
        int i = RANDOM.nextInt(all.length);
        if (all.length > 1 && i == lastIndex) i = (i + 1) % all.length;
        lastIndex = i;
        deliver(all[i], "");
    }

    private void deliver(String text, String author) {
        shownText = text;
        shownAuthor = author;
        shownMode = mode;
        shownAt = SystemClock.elapsedRealtime();
        listener.onQuote(text, author);
    }

    // ---- network (worker thread) -------------------------------------------------------------------

    private void fetch(String today) {
        prefs.edit().putLong("tried", System.currentTimeMillis()).apply();
        if (io == null) {
            io = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "quotes");
                t.setDaemon(true);
                return t;
            });
        }
        final ExecutorService mine = io;
        mine.execute(() -> {
            String[] quote = null;
            boolean answered = false;
            try {
                String body = get(URL_TODAY);
                answered = true;
                quote = parse(body);
            } catch (Exception e) {
                Log.w(TAG, "quote fetch failed: " + e);
            }
            final String[] result = quote;
            final boolean done = answered;
            main.post(() -> {
                if (!started || io != mine) return;
                if (done) {
                    SharedPreferences.Editor e = prefs.edit().putString("day", today)
                            .putString("text", result == null ? "" : result[0])
                            .putString("author", result == null ? "" : result[1]);
                    e.apply();
                }
                if (result != null) deliver(result[0], result[1]);
            });
        });
    }

    /** {text, author with credit}, or null when the answer is the rate-limit notice, empty or too long. */
    static String[] parse(String body) throws Exception {
        JSONArray list = new JSONArray(body);
        if (list.length() == 0) return null;
        JSONObject first = list.getJSONObject(0);
        String text = first.optString("q", "").trim();
        String author = first.optString("a", "").trim();
        if (text.isEmpty() || text.length() > MAX_LENGTH) return null;
        // When the free quota is used up the service answers with a notice, author "zenquotes.io".
        if (CREDIT.equalsIgnoreCase(author) || text.toLowerCase(java.util.Locale.ROOT).contains("too many requests")) {
            return null;
        }
        return new String[]{text, (author.isEmpty() ? "" : author + " · ") + CREDIT};
    }

    private static String get(String address) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("User-Agent", "TvHome/3.4.1");
            c.setRequestProperty("Accept", "application/json");
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + c.getResponseCode());
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[2048];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BODY) throw new IOException("response too large");
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }
}
