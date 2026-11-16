# KurCep

Native Android app and home-screen widget showing USD/TRY, EUR/TRY, and estimated 24K gram gold in TRY.

## Run

Open this folder in Android Studio, allow Gradle sync, then run the `app` configuration on an Android 7.0+ device or emulator. Add the widget by long-pressing the home screen and choosing **Widgets → KurCep**.

No API key is required. Currency and gold data come from the Turkish market feed
[Truncgil Finans](https://finans.truncgil.com/v4/today.json): intraday USD/TRY, EUR/TRY and gram
gold with buy/sell quotes and daily change, in one request. The app headlines the selling price
and shows buy/sell on the main screen.

If that feed fails, returns an invalid schema, or is older than 3 days, the app falls back to the
global sources: [ExchangeRate-API](https://www.exchangerate-api.com) (USD/TRY, EUR/TRY, updated once
daily), Gold API spot XAU/USD for gram gold, and Frankfurter / XAUS for daily changes. Brent is
always read from Yahoo Finance and is optional.

The main screen and widget show the **provider's data time and source** (for example
`Veri saati 18:17 • Kaynak: Truncgil Finans`), not merely when the app fetched, so stale fallback
data is visibly stale. All values are indicative, not trading quotes.

## Build

Use JDK 17 or newer:

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew assembleDebug
```

The `release` build enables R8 shrinking and resource shrinking. A production-signed APK/AAB still requires configuring an application-specific keystore; `assembleDebug` remains available for local device installs.

For a signed release, provide the Gradle properties `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` through a local untracked `~/.gradle/gradle.properties` file or CI secrets.

The widget renders cached data immediately and queues a unique, network-constrained WorkManager job for the refresh. This keeps the work recoverable if the app process is reclaimed while the APIs are running. OEM battery optimization may still delay the system's 30-minute widget refresh interval.

## Architecture and verification

`RefreshCoordinator` coordinates one single-flight refresh through `RateRepository` and its
dedicated Turkish-market, FX, gold, Brent, JSON, and HTTP source classes. Results are represented as an immutable `RateSnapshot` made
of `RateQuote` values, then persisted atomically with a monotonic refresh ID. Optional source
failures are represented as `RateQuote.Status.UNAVAILABLE`, separately from a missing daily
change. UI and widget formatting share the same pure-Java formatter.

Run the local quality gates with:

```bash
./gradlew clean test lint assembleDebug assembleRelease jacocoTestDebugUnitTestCoverageVerification
```

The JVM tests run with Android default stubs disabled, and the deterministic domain/network
coverage gate requires at least 70% line coverage. CI additionally runs secret scanning,
CodeQL, dependency review, and Dependabot updates.

The manual/tag-triggered `Release package` workflow produces unsigned AAB/APK artifacts. A
production-signed package still requires the release keystore properties described above.
