# Markets

Indices, commodities, currencies and stocks on the TV, as a full-screen page and a card on the launcher's Home.
Four pages, Left and Right to change page, OK to refresh:

| Page | What it shows |
|---|---|
| Indices | S&P 500, Nasdaq Composite, Dow Jones, DAX, FTSE MIB, CAC 40, FTSE 100, Nikkei 225 |
| Commodities & FX | Gold, silver, WTI crude oil, EUR/USD, Bitcoin, Ethereum |
| US stocks | Apple, Microsoft, Nvidia, Tesla, Amazon, Alphabet |
| Italian stocks | Ferrari, Enel, Intesa Sanpaolo, UniCredit, Eni, Stellantis, Generali |

Each row has the latest price and the change since the previous close, in green or red with the sign always
written (so it reads without colour). The Italian interface is in `values-it`; the card label stays English
because the module-card spec takes literal text.

**Not financial advice.** Prices may be delayed and can be wrong. Do not trade on a TV page.

## Where the data comes from

[Yahoo Finance](https://finance.yahoo.com/)'s public chart endpoint (`query1.finance.yahoo.com/v8/finance/chart`),
which needs no account or key. **That endpoint is not a documented or supported API**: Yahoo can change it, limit
it or remove it without notice, and its terms keep it to personal use. If it stops working, the page says so and
shows the last readings it kept. No other free source I tried needed no key (Stooq, which used to, now returns
an error). To use another provider, replace `YahooClient`; nothing else depends on it.

The change shown is against the previous session's close, taken from the daily bars: the chart's own
"previous close" field is the close before the whole five-day window, which would make every change look like
a week's.

## Cost on the TV

- **No background service, no alarm, no start at boot.** It reads data only while its page is on screen.
- Only the **visible page's** instruments are read (6 to 8 requests), at most every two minutes, on three short-lived
  threads. The last readings are kept in SharedPreferences, so the page is never empty on a slow network.
- No libraries: plain Android framework classes, drawn in code, so the APK stays small.

## Permissions and privacy

`INTERNET`, used only for those requests. The only thing sent is the list of symbols on the visible page, to
`query1.finance.yahoo.com`, with a generic user agent. No account, identifier, location or telemetry.

## Change the instruments

Edit `Catalog.java`: one `Item(symbol, name, kind)` row per instrument, in the order shown. Symbols are Yahoo's
(`^GSPC`, `FTSEMIB.MI`, `AAPL`, `EURUSD=X`, `BTC-USD`, ...). `kind` is `INDEX` (points), `RATE` (four decimals) or
`PRICE` (with the currency sign). Add pages by adding an array to `PAGES` and a title to `PAGE_TITLES`.

## Build and install

JDK 17 and the Android SDK (platform 34), then from this folder:

```sh
./gradlew assembleRelease      # Windows: gradlew.bat assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell input keyevent KEYCODE_HOME
```

It is signed with the debug key, for sideloading. With the [launcher](../../launcher/) in this repo, answer **Show** on
the prompt that appears and the card takes the app's place on Home. On any other home it is an ordinary Android TV
app. The package is `io.github.salvomorph.stocks`.

## Tested on

Philips 55POS9002/12 (Android TV 8.0.0, 1.9 GB RAM). Not tested on other models.

Quote data © Yahoo and its sources. This project is not affiliated with Yahoo.
