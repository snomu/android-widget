# term

A personal Android home screen widget: world clocks, a 6-day weather table per city, and a line of market numbers, drawn as colored monospace text on black.

It is distributed through [GitHub Releases](https://github.com/snomu/android-widget/releases) and installed with [Obtainium](https://github.com/ImranR98/Obtainium). There is no Play Store listing and no tracking or analytics.

Licensed under [PolyForm Noncommercial 1.0.0](https://polyformproject.org/licenses/noncommercial/1.0.0/).

## What it does

- A 4×2 widget (resizable) built with a classic `AppWidgetProvider` and `RemoteViews`.
- Clocks are `TextClock` views, so they tick every minute inside the launcher without waking the app.
- Weather comes from Open-Meteo, stocks from Finnhub, and crypto from CoinGecko.
- Weather and markets refresh every 30 minutes and when you tap the widget.
- The last values are cached. If a refresh fails they stay on screen, and anything more than 2 hours old is shown in a dim color.
- A ticker with no data yet (for example before a Finnhub key is entered) shows `—`.
- `⁺` after a clock means that city is already on the next calendar day relative to the home city.

## Privacy and permissions

The app makes HTTPS requests to three hosts and nothing else:

| Host | What is sent |
|---|---|
| `api.open-meteo.com` | the coordinates of your cities |
| `finnhub.io` | your stock symbols and your API key (in a header) |
| `api.coingecko.com` | your crypto ids |

It requests `INTERNET`. WorkManager, which runs the 30-minute refresh, adds `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, and `FOREGROUND_SERVICE`.

The Finnhub key is encrypted with a key held in the Android Keystore and stored only on the device. App data is excluded from cloud backup and device transfer.

## Setup

### 1. Generate a signing keystore (once)

Run this somewhere outside the repository, or in it: `*.keystore` and `*.base64` are git-ignored.

```bash
keytool -genkeypair -v \
  -keystore term.keystore \
  -alias term \
  -keyalg RSA -keysize 2048 -validity 10000
```

**Back up `term.keystore` and its passwords somewhere durable.** Android only installs an update over an existing app when both are signed with the same key. If the keystore is lost, you have to uninstall and reinstall, which also clears your settings.

Encode it for GitHub Actions:

```bash
base64 -w0 term.keystore > term.keystore.base64
```

On macOS use `base64 -i term.keystore -o term.keystore.base64`.

### 2. Add the repository secrets

In the repository, open **Settings → Secrets and variables → Actions** and create:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the contents of `term.keystore.base64` |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | the alias used above, `term` |
| `KEY_PASSWORD` | the key password (the same as the keystore password unless you chose otherwise) |

Delete `term.keystore.base64` afterwards. Never commit the keystore, its encoded form, or the passwords.

### 3. Cut a release

```bash
git tag v0.1.0
git push origin v0.1.0
```

Pushing a `v*` tag runs the [release workflow](.github/workflows/release.yml). It runs the unit tests, builds a signed APK, checks the signature, and attaches `term-<version>.apk` to a GitHub Release named after the tag.

- `versionName` is the tag without the leading `v`. Tags must look like `v1.2.3`.
- `versionCode` is the workflow's run number, so each release is newer than the last. Do not rename or recreate the workflow file: the run number would start again at 1 and Android would refuse the update as a downgrade.

### 4. Add the app in Obtainium

1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. Tap **Add app**, enter `https://github.com/snomu/android-widget` as the source URL, and add it.
3. Install. Obtainium picks up later releases from the same page.
4. Long-press the home screen, choose **Widgets**, and add **term**. You can also open the **term** app and tap **Add widget to home screen**.

### 5. Enter your Finnhub key

1. Create a free account at [finnhub.io](https://finnhub.io/) and copy your API key.
2. Open the **term** app.
3. Paste the key under **Finnhub API key** and tap **Save key**.

Weather and crypto work without a key. The key is write-only in the app: it is never displayed again, only replaced or cleared.

## Settings

The **term** app is the settings screen. Long-pressing the widget and choosing its settings or reconfigure option opens the same screen.

- **Cities**: a 3-character code, latitude, longitude, an IANA time zone such as `Europe/Paris`, and which city is home. The home city sets the day columns and is shown in white at full brightness.
- **Tickers**: a label, a symbol, a source (`FINNHUB` for stocks and ETFs, `COINGECKO` for crypto, using the CoinGecko id such as `helium`), and whether to show the price or the day's percent change.

Defaults:

| City | Latitude | Longitude | Time zone |
|---|---|---|---|
| NYC (home) | 40.69 | -73.92 | America/New_York |
| SEA | 47.61 | -122.33 | America/Los_Angeles |
| LON | 51.51 | -0.13 | Europe/London |
| BJS | 39.90 | 116.41 | Asia/Shanghai |
| TYO | 35.68 | 139.69 | Asia/Tokyo |

Tickers: HNT (price, CoinGecko id `helium`), S&P (percent, via SPY), GOOG (percent). Add your own under **Tickers** in the app.

## Building locally

You need JDK 17 and the Android SDK. Point Gradle at the SDK with the `ANDROID_HOME` environment variable or an `sdk.dir=` line in `local.properties` (git-ignored).

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Debug builds use the application id `io.github.snomu.termwidget.debug`, so they install beside a release build. They also include a `WidgetPreviewActivity` that renders the widget in an ordinary activity:

```bash
adb shell am start -n io.github.snomu.termwidget.debug/io.github.snomu.termwidget.debug.WidgetPreviewActivity
```

`./gradlew assembleRelease` signs the APK only when `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` are set in the environment. Without them it produces an unsigned APK.
