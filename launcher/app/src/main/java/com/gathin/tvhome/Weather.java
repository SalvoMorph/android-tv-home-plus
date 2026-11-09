package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Current weather for one place, from Open-Meteo (free, no account, no API key).
 *
 * Built to cost the TV almost nothing: nothing runs until {@link #start} (Home resumed) and everything
 * stops in {@link #stop} (Home paused). One small HTTPS request every 30 minutes at most, a 5 minute retry
 * after a failure, one short-lived worker thread, and the last answer cached in SharedPreferences so Home
 * shows the weather at once. Only the place's coordinates leave the TV; there is no account, identifier
 * or location permission involved.
 *
 * The place is a city name from {@link #PRESETS}, a "lat,lon" or "lat,lon,Name" pair, or any other name
 * (looked up once with Open-Meteo's geocoding and remembered).
 */
final class Weather {
    private static final String TAG = "TvHome.Weather";
    private static final String PREFS = "weather_cache";
    static final long REFRESH_MS = 30 * 60_000L;
    private static final long RETRY_MS = 5 * 60_000L;
    private static final int MAX_BODY = 64 * 1024;
    private static final int TIMEOUT_MS = 8_000;

    enum Kind { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, STORM }

    static final class City {
        final String name;
        final double lat;
        final double lon;

        City(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    /** Cities offered in the on-TV picker; any other place is set with weather_city in a theme. */
    static final City[] PRESETS = {
            new City("Roma", 41.89, 12.48), new City("Milano", 45.46, 9.19),
            new City("Napoli", 40.85, 14.27), new City("Torino", 45.07, 7.69),
            new City("Palermo", 38.12, 13.36), new City("Genova", 44.41, 8.93),
            new City("Bologna", 44.49, 11.34), new City("Firenze", 43.77, 11.26),
            new City("Bari", 41.12, 16.87), new City("Catania", 37.50, 15.09),
            new City("Venezia", 45.44, 12.32), new City("Verona", 45.44, 10.99),
            new City("Messina", 38.19, 15.55), new City("Padova", 45.41, 11.88),
            new City("Trieste", 45.65, 13.78), new City("Brescia", 45.54, 10.22),
            new City("Bergamo", 45.70, 9.67), new City("Monza", 45.58, 9.27),
            new City("Parma", 44.80, 10.33), new City("Modena", 44.65, 10.93),
            new City("Livorno", 43.55, 10.31), new City("Perugia", 43.11, 12.39),
            new City("Ancona", 43.62, 13.52), new City("Pescara", 42.46, 14.21),
            new City("Latina", 41.47, 12.90), new City("Foggia", 41.46, 15.55),
            new City("Salerno", 40.68, 14.77), new City("Lecce", 40.35, 18.17),
            new City("Taranto", 40.47, 17.24), new City("Cosenza", 39.30, 16.25),
            new City("Reggio Calabria", 38.11, 15.65), new City("Cagliari", 39.22, 9.12),
            new City("Sassari", 40.73, 8.56), new City("Bolzano", 46.50, 11.35),
            new City("Trento", 46.07, 11.12), new City("Udine", 46.06, 13.24),
            new City("Vicenza", 45.55, 11.55),
    };

    static final class Report {
        final String key;
        final String place;
        final int temp;
        final int min;
        final int max;
        final Kind kind;
        final boolean day;
        final long at;

        Report(String key, String place, int temp, int min, int max, Kind kind, boolean day, long at) {
            this.key = key;
            this.place = place;
            this.temp = temp;
            this.min = min;
            this.max = max;
            this.kind = kind;
            this.day = day;
            this.at = at;
        }
    }

    interface Listener {
        /** Always called on the main thread. */
        void onReport(Report report);
    }

    private static final class Place {
        final String name;
        final double lat;
        final double lon;

        Place(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    private final SharedPreferences prefs;
    private final String key;
    private final Handler main;
    private final Listener listener;
    private final Runnable tick = this::refresh;
    /** Main thread only. */
    private ExecutorService io;
    private boolean started;

    Weather(Context ctx, String city, Handler main, Listener listener) {
        this.prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.key = normalize(city);
        this.main = main;
        this.listener = listener;
    }

    /** Shows the cached answer at once, then refreshes when it is older than {@link #REFRESH_MS}. */
    void start() {
        if (started || key.isEmpty()) return;
        started = true;
        Report cached = readCache();
        if (cached != null) listener.onReport(cached);
        long age = cached == null ? Long.MAX_VALUE : System.currentTimeMillis() - cached.at;
        if (age >= REFRESH_MS || age < 0) refresh();
        else main.postDelayed(tick, REFRESH_MS - age);
    }

    void stop() {
        started = false;
        main.removeCallbacks(tick);
        if (io != null) {
            io.shutdownNow();
            io = null;
        }
    }

    private void refresh() {
        if (!started) return;
        if (io == null) {
            io = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "weather");
                t.setDaemon(true);
                return t;
            });
        }
        final ExecutorService mine = io;
        mine.execute(() -> {
            Report fetched = null;
            try {
                fetched = fetch();
            } catch (Exception e) {
                Log.w(TAG, "weather fetch failed: " + e);
            }
            final Report result = fetched;
            main.post(() -> {
                if (!started || io != mine) return;
                if (result != null) {
                    writeCache(result);
                    listener.onReport(result);
                }
                main.removeCallbacks(tick);
                main.postDelayed(tick, result != null ? REFRESH_MS : RETRY_MS);
            });
        });
    }

    // ---- network (worker thread) -------------------------------------------------------------------

    private Report fetch() throws Exception {
        Place place = locate();
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,weather_code,is_day"
                        + "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
                place.lat, place.lon);
        JSONObject root = new JSONObject(get(url));
        JSONObject current = root.getJSONObject("current");
        JSONObject daily = root.getJSONObject("daily");
        return new Report(key, place.name,
                (int) Math.round(current.getDouble("temperature_2m")),
                (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)),
                (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)),
                kindOf(current.getInt("weather_code")),
                current.optInt("is_day", 1) == 1,
                System.currentTimeMillis());
    }

    private Place locate() throws Exception {
        for (City c : PRESETS) {
            if (normalize(c.name).equals(key)) return new Place(c.name, c.lat, c.lon);
        }
        Place pair = parsePair(key);
        if (pair != null) return pair;
        String cached = prefs.getString("geo_" + key, null);
        if (cached != null) {
            Place p = parsePair(cached);
            if (p != null) return p;
        }
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=it&format=json&name="
                + URLEncoder.encode(key, "UTF-8");
        JSONObject first = new JSONObject(get(url)).getJSONArray("results").getJSONObject(0);
        Place p = new Place(first.optString("name", key), first.getDouble("latitude"), first.getDouble("longitude"));
        prefs.edit().putString("geo_" + key,
                String.format(Locale.US, "%.4f,%.4f,%s", p.lat, p.lon, p.name)).apply();
        return p;
    }

    /** "lat,lon" or "lat,lon,Name"; null when the text is anything else. */
    private static Place parsePair(String text) {
        String[] parts = text.split(",", 3);
        if (parts.length < 2) return null;
        try {
            double lat = Double.parseDouble(parts[0].trim());
            double lon = Double.parseDouble(parts[1].trim());
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
            String name = parts.length == 3 && !parts[2].trim().isEmpty() ? parts[2].trim() : "";
            return new Place(name, lat, lon);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String get(String address) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("User-Agent", "TvHome/2.5");
            c.setRequestProperty("Accept", "application/json");
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + c.getResponseCode());
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
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

    // ---- cache -------------------------------------------------------------------------------------

    private Report readCache() {
        String text = prefs.getString("report", null);
        if (text == null) return null;
        try {
            JSONObject o = new JSONObject(text);
            if (!key.equals(o.optString("key"))) return null;   // another city was chosen since
            return new Report(key, o.getString("place"), o.getInt("temp"), o.getInt("min"), o.getInt("max"),
                    Kind.valueOf(o.getString("kind")), o.getBoolean("day"), o.getLong("at"));
        } catch (Exception e) {
            return null;
        }
    }

    private void writeCache(Report r) {
        try {
            JSONObject o = new JSONObject();
            o.put("key", r.key).put("place", r.place).put("temp", r.temp).put("min", r.min).put("max", r.max)
                    .put("kind", r.kind.name()).put("day", r.day).put("at", r.at);
            prefs.edit().putString("report", o.toString()).apply();
        } catch (Exception ignored) {
            // the cache is only a shortcut
        }
    }

    // ---- helpers -----------------------------------------------------------------------------------

    static String normalize(String city) {
        return city == null ? "" : city.trim().toLowerCase(Locale.ROOT);
    }

    /** WMO weather interpretation codes, as Open-Meteo returns them. */
    static Kind kindOf(int code) {
        if (code == 0 || code == 1) return Kind.CLEAR;
        if (code == 2) return Kind.PARTLY;
        if (code == 3) return Kind.CLOUDY;
        if (code == 45 || code == 48) return Kind.FOG;
        if (code >= 51 && code <= 67) return Kind.RAIN;
        if (code >= 71 && code <= 77) return Kind.SNOW;
        if (code >= 80 && code <= 82) return Kind.RAIN;
        if (code == 85 || code == 86) return Kind.SNOW;
        if (code >= 95) return Kind.STORM;
        return Kind.CLOUDY;
    }
}
