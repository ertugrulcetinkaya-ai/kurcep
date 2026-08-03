# KurCep

Native Android app and home-screen widget showing USD/TRY, EUR/TRY, and estimated 24K gram gold in TRY.

## Run

Open this folder in Android Studio, allow Gradle sync, then run the `app` configuration on an Android 6.0+ device or emulator. Add the widget by long-pressing the home screen and choosing **Widgets → KurCep**.

No API key is required. Currency rates use ExchangeRate-API; gram gold is derived from the XAU/USD spot price supplied by Gold API and USD/TRY. Values are indicative, not trading quotes.
