# term

A personal Android home screen widget: world clocks, a 6-day weather table per city, and a line of market numbers, rendered as colored monospace text on black.

Distributed through [GitHub Releases](https://github.com/snomu/android-widget/releases) and installed with [Obtainium](https://github.com/ImranR98/Obtainium). No Play Store. No tracking or analytics.

Licensed under [PolyForm Noncommercial 1.0.0](https://polyformproject.org/licenses/noncommercial/1.0.0/).

## Features

- 4×2 classic `AppWidgetProvider` with live `TextClock`s (tick every minute without waking the app)
- Open-Meteo weather (no API key)
- Finnhub equities + CoinGecko crypto
- 30-minute WorkManager refresh, plus tap-to-refresh
- Offline cache via DataStore; values dim after 2 hours if stale
- Settings for Finnhub key, cities, and tickers

## Install with Obtainium

1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. Add app → GitHub → `https://github.com/snomu/android-widget`.
3. Prefer releases / APK assets named `term-*.apk`.
4. Install, place the **term** widget on your home screen, then open **term** settings and paste your Finnhub API key.

## Finnhub API key

1. Create a free account at [finnhub.io](https://finnhub.io/).
2. Copy your API key.
3. Open the **term** app (launcher icon or widget configure flow).
4. Paste the key under **Finnhub API key** and tap **Save key**.

The key is encrypted with an Android Keystore-backed AES key. It is never hardcoded or committed.

## Cut a release

### 1. Generate a release keystore (once)

```bash
keytool -genkeypair -v \
  -keystore term.keystore \
  -alias term \
  -keyalg RSA -keysize 2048 -validity 10000
```

**Back up `term.keystore` and both passwords somewhere durable.** Losing them means future APKs cannot update the installed app in place; you would have to uninstall and reinstall.

Encode the keystore for GitHub Actions:

```bash
base64 -w0 term.keystore > term.keystore.base64
```

### 2. Add GitHub Actions secrets

In the repo: **Settings → Secrets and variables → Actions**, create:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | contents of `term.keystore.base64` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | e.g. `term` |
| `KEY_PASSWORD` | key password |

### 3. Tag and push

```bash
git tag v0.1.0
git push origin v0.1.0
```

The [release workflow](.github/workflows/release.yml) builds a signed APK, names it `term-<version>.apk`, and attaches it to a GitHub Release for that tag. `versionName` comes from the tag (without the leading `v`); `versionCode` is the workflow run number.

## Local development

```bash
# requires Android SDK; see local.properties sdk.dir
./gradlew assembleDebug
./gradlew test
```

Debug builds use application id `io.github.snomu.termwidget.debug` so they can sit beside a release install.

## Defaults

| City | Lat | Lon | Zone |
|---|---|---|---|
| NYC (home) | 40.69 | -73.92 | America/New_York |
| SEA | 47.61 | -122.33 | America/Los_Angeles |
| LON | 51.51 | -0.13 | Europe/London |
| BJS | 39.90 | 116.41 | Asia/Shanghai |
| TYO | 35.68 | 139.69 | Asia/Tokyo |

Tickers: HNT/helium (price), S&P via SPY (percent), GOOG (percent).
