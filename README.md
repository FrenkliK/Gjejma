# Gjejma 🇦🇱

**Gjej çmimin. Jo xhiron.** Gjejma is an open-source Albanian price comparison project. Search once and see offers collected from Albanian-market shops, then buy directly from the retailer. Gjejma does not sell products or handle checkout.

This repository contains the website and Node.js API/collector, plus a standalone native Android app. The Android app collects supported public feeds directly on the phone, stores its catalogue locally, and does not need the Node server or another computer.

## Download and use the Android app by itself

1. Open [Gjejma Releases](https://github.com/FrenkliK/Gjejma/releases/latest).
2. Under **Assets**, download `Gjejma.apk`. You do not need the source ZIP or repository.
3. Open the APK on your Android phone and approve Android's one-time prompt to install apps from your browser or file manager.
4. Open **Gjejma**, tap **Rifresko dyqanet** while connected to the internet, then search products. Later searches use the catalogue saved on the phone and work offline.

A network connection is needed to download the store directory and refresh prices. Initial data is empty; the app never fills the screen with invented sample offers. Coverage depends on stores that expose reachable public feeds, their `robots.txt` rules and access policies, and network availability. Prices and stock can change, so confirm on the seller's site before buying.

Every push to `main` builds an APK under **Actions → Android app** (artifact expires after 30 days). A version tag triggers **Android release APK**, which attaches a standalone APK to a permanent GitHub Release. Debug APKs are signed for sideloading and testing, not Play Store distribution.

## Start the website locally

1. Install [Node.js 20 or newer](https://nodejs.org/). No `npm install` is needed.
2. Download or clone the repository.
3. Double-click `start.bat` on Windows, or run `sh ./start.sh` on macOS/Linux.
4. Open `http://localhost:3000`. The server attempts collection on startup and then every six hours.

Search results appear after stores return product data. The first refresh may take a few minutes. If a site blocks automated requests or has no supported feed, it is skipped and the failure is recorded.

## Features

- Search shop listings and sort by price or retailer.
- Open the original listing on the seller site.
- See reported availability and collection time.
- Inspect 27 configured store candidates; disabled candidates are not treated as confirmed sources.
- Website users can save searches and price targets locally in their browser.
- Android users refresh their phone-local catalog and search it offline.
- The backend exposes health, shop status, products, offers, and price-history APIs.

## Build the Android app

Open `android/` in Android Studio with JDK 17 and Android SDK Platform 36 installed. This project uses Gradle 9.3.1 and omits generated Gradle wrapper binaries. With Gradle 9.3.1 installed, build with:

```sh
gradle -p android assembleDebug
```

The output is `android/app/build/outputs/apk/debug/app-debug.apk`. Google Play publication requires a separately managed release signing key and Play Console setup. Never commit signing keys or passwords.

## Run the website backend

On Windows, double-click `start.bat`; on macOS/Linux, run `sh ./start.sh`. Or run `node server/server.js`. Use `Ctrl+C` to stop it. `collect.bat` or `npm run collect` starts a manual refresh.

The Node collector checks shop robots policy, uses request timeouts and delays, and supports public WooCommerce Store API feeds and JSON-LD product data. A generic adapter can fail when a site blocks requests, requires JavaScript/login, changes its feed format, or forbids access. Gjejma does not bypass logins, CAPTCHAs, or anti-bot protections.

## Repository layout

```text
web/                         responsive Albanian website
server/server.js             static server and JSON API
server/collector.js          scheduled retailer collector
server/stores.json            retailer directory and adapter settings
server/db.json.example        empty database template
android/                      standalone Android app and Android Studio project
docs/ARCHITECTURE.md          components, data model, and API details
.github/workflows/android.yml builds a downloadable APK artifact
.github/workflows/android-release.yml attaches APK to version Releases
start.bat / start.sh          local website launch shortcuts
collect.bat                   manual backend catalogue refresh
```

## API

- `GET /api/health`
- `GET /api/shops`
- `GET /api/search?q=iphone`
- `GET /api/products/:id`
- `GET /api/history/:id`
- `POST /api/collect` (local server only)

Set `PORT` to change the web server port and `AUTO_COLLECT=false` to disable scheduled backend collection. Runtime catalogue data stays in the ignored local `server/db.json`.

## License

MIT. See [LICENSE](LICENSE).



