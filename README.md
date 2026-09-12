# KurCep

Native Android app and home-screen widget showing USD/TRY, EUR/TRY, and estimated 24K gram gold in TRY.

## Run

Open this folder in Android Studio, allow Gradle sync, then run the `app` configuration on an Android 6.0+ device or emulator. Add the widget by long-pressing the home screen and choosing **Widgets → KurCep**.

No API key is required. Current currency rates use ExchangeRate-API. Gram gold is derived from the XAU/USD spot price supplied by Gold API and USD/TRY. Daily currency changes use Frankfurter's previous-day reference rates; daily gold changes use the previous daily close from XAUS and the previous USD/TRY reference rate. These services are public, unauthenticated dependencies and all values are indicative, not trading quotes.

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
dedicated FX, gold, Brent, JSON, and HTTP source classes. Results are represented as an immutable `RateSnapshot` made
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
