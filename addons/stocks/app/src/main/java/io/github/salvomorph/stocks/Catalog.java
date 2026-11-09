package io.github.salvomorph.stocks;

/**
 * What the pages show. Symbols are Yahoo Finance's. To add or change an instrument, edit {@link #PAGES}: one
 * row per instrument, in the order shown. {@code Kind} decides how the price is written: an index in points,
 * an exchange rate with four decimals, anything else in its currency.
 */
final class Catalog {
    enum Kind { INDEX, RATE, PRICE }

    static final class Item {
        final String symbol;
        final String name;
        final Kind kind;

        Item(String symbol, String name, Kind kind) {
            this.symbol = symbol;
            this.name = name;
            this.kind = kind;
        }
    }

    static final int[] PAGE_TITLES = {
            R.string.page_indices, R.string.page_markets, R.string.page_us, R.string.page_italy,
    };

    static final Item[][] PAGES = {
            {
                    new Item("^GSPC", "S&P 500", Kind.INDEX),
                    new Item("^IXIC", "Nasdaq Composite", Kind.INDEX),
                    new Item("^DJI", "Dow Jones", Kind.INDEX),
                    new Item("^GDAXI", "DAX", Kind.INDEX),
                    new Item("FTSEMIB.MI", "FTSE MIB", Kind.INDEX),
                    new Item("^FCHI", "CAC 40", Kind.INDEX),
                    new Item("^FTSE", "FTSE 100", Kind.INDEX),
                    new Item("^N225", "Nikkei 225", Kind.INDEX),
            },
            {
                    new Item("GC=F", "Gold", Kind.PRICE),
                    new Item("SI=F", "Silver", Kind.PRICE),
                    new Item("CL=F", "WTI crude oil", Kind.PRICE),
                    new Item("EURUSD=X", "EUR / USD", Kind.RATE),
                    new Item("BTC-USD", "Bitcoin", Kind.PRICE),
                    new Item("ETH-USD", "Ethereum", Kind.PRICE),
            },
            {
                    new Item("AAPL", "Apple", Kind.PRICE),
                    new Item("MSFT", "Microsoft", Kind.PRICE),
                    new Item("NVDA", "Nvidia", Kind.PRICE),
                    new Item("TSLA", "Tesla", Kind.PRICE),
                    new Item("AMZN", "Amazon", Kind.PRICE),
                    new Item("GOOGL", "Alphabet", Kind.PRICE),
            },
            {
                    new Item("RACE.MI", "Ferrari", Kind.PRICE),
                    new Item("ENEL.MI", "Enel", Kind.PRICE),
                    new Item("ISP.MI", "Intesa Sanpaolo", Kind.PRICE),
                    new Item("UCG.MI", "UniCredit", Kind.PRICE),
                    new Item("ENI.MI", "Eni", Kind.PRICE),
                    new Item("STLAM.MI", "Stellantis", Kind.PRICE),
                    new Item("G.MI", "Generali", Kind.PRICE),
            },
    };

    private Catalog() {}
}
