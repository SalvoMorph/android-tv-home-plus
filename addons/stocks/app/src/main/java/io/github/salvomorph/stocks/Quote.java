package io.github.salvomorph.stocks;

/** One reading: the latest price, the previous session's close (NaN when unknown) and when it was taken. */
final class Quote {
    final String symbol;
    final double price;
    final double previous;
    final String currency;
    /** Epoch seconds of the reading, as the exchange reports it; 0 when unknown. */
    final long time;

    Quote(String symbol, double price, double previous, String currency, long time) {
        this.symbol = symbol;
        this.price = price;
        this.previous = previous;
        this.currency = currency;
        this.time = time;
    }

    boolean hasChange() {
        return !Double.isNaN(previous) && previous != 0;
    }

    /** Percent change since the previous close; 0 when it is not known (check {@link #hasChange}). */
    double percent() {
        return hasChange() ? (price - previous) / previous * 100.0 : 0;
    }
}
