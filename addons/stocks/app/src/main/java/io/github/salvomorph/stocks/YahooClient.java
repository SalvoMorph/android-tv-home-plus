package io.github.salvomorph.stocks;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads one instrument from Yahoo Finance's public chart endpoint (no account, no key).
 *
 * That endpoint is not a documented, supported API: Yahoo can change or limit it without notice, and its terms
 * keep it to personal use. Prices may be delayed. If it stops working the page says so and shows the last
 * readings it kept; nothing else in the app depends on it.
 */
final class YahooClient {
    private static final String URL_PREFIX = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_BODY = 256 * 1024;

    private YahooClient() {}

    static Quote fetch(String symbol) throws IOException, JSONException {
        String url = URL_PREFIX + URLEncoder.encode(symbol, "UTF-8") + "?interval=1d&range=5d";
        return parse(symbol, get(url));
    }

    /**
     * The latest price is the exchange's regular market price. The previous close is the daily bar before the
     * latest one: the chart's own "previous close" field is the close before the whole five-day window, which
     * would make every change look like a week's.
     */
    static Quote parse(String symbol, String body) throws JSONException {
        JSONObject chart = new JSONObject(body).getJSONObject("chart");
        JSONArray results = chart.optJSONArray("result");
        if (results == null || results.length() == 0) throw new JSONException("no result for " + symbol);
        JSONObject result = results.getJSONObject(0);
        JSONObject meta = result.getJSONObject("meta");
        double price = meta.getDouble("regularMarketPrice");
        long time = meta.optLong("regularMarketTime", 0);
        long offset = meta.optLong("gmtoffset", 0);
        String currency = meta.optString("currency", "");

        List<Long> days = new ArrayList<>();
        List<Double> closes = new ArrayList<>();
        JSONArray stamps = result.optJSONArray("timestamp");
        JSONObject indicators = result.optJSONObject("indicators");
        JSONArray quote = indicators == null ? null : indicators.optJSONArray("quote");
        JSONArray close = quote == null || quote.length() == 0 ? null : quote.getJSONObject(0).optJSONArray("close");
        if (stamps != null && close != null) {
            int n = Math.min(stamps.length(), close.length());
            for (int i = 0; i < n; i++) {
                double c = close.optDouble(i, Double.NaN);   // a missing bar is null in the answer
                if (Double.isNaN(c)) continue;
                days.add(Math.floorDiv(stamps.getLong(i) + offset, 86_400L));
                closes.add(c);
            }
        }
        double previous = Double.NaN;
        int n = closes.size();
        if (n > 0) {
            long today = Math.floorDiv(time + offset, 86_400L);
            // The last bar is the session in progress (or just ended) when it falls on the reading's day.
            boolean lastIsToday = days.get(n - 1) == today;
            if (lastIsToday) {
                if (n >= 2) previous = closes.get(n - 2);
            } else {
                previous = closes.get(n - 1);
            }
        }
        return new Quote(symbol, price, previous, currency, time);
    }

    private static String get(String address) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) Markets/1.0");
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
}
