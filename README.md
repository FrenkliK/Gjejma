# Gjejma 🇦🇱

**Gjej çmimin. Jo xhiron.** Gjejma is an open-source Albanian price comparison project. Search once and see offers collected from Albanian-market shops, then buy directly from the retailer. Gjejma does not sell products or handle checkout.

This repository contains the website and Node.js API, plus a standalone native Android app. Both website and Android searches contact enabled Albanian retailers at the time of each search. Search results are returned for that request and are not written to a local or server catalogue. The Android APK needs no Gjejma server or other computer.

## Download and use the Android app by itself

1. Open [Gjejma Releases](https://github.com/FrenkliK/Gjejma/releases/latest).
2. Under **Assets**, download `Gjejma.apk`. You do not need the source ZIP or repository.
3. Open the APK on your Android phone and approve Android's one-time prompt to install apps from your browser or file manager.
4. Open **Gjejma**, enter a product and tap **Kërko tani**. The app sends the search directly from the phone to each enabled shop and shows current responses.

A network connection is required for every search. Product searches and results are not saved by the app. Coverage depends on store availability, the store's search/catalogue format, and network access. A store can return an actual HTTP error (including 403 or 404), which Gjejma reports without trying to bypass access controls. For stores where the project has permission to query, `robots.txt` is not used as an authorization gate. Prices and stock can change, so confirm on the seller's site before buying.

Every push to `main` builds an APK under **Actions → Android app** (artifact expires after 30 days). Every Android app change on `main` also creates a permanent GitHub Release with the standalone `Gjejma.apk`; no tag or repository download is required. Version tags can also create named releases. Debug APKs are signed for sideloading and testing, not Play Store distribution.

## Start the website locally

1. Install [Node.js 20 or newer](https://nodejs.org/). No `npm install` is needed.
2. Download or clone the repository.
3. Double-click `start.bat` on Windows, or run `sh ./start.sh` on macOS/Linux.
4. Open `http://localhost:3000`. Each search queries the enabled shop websites live; the server does not start a scheduled catalogue collection by default.

Search results appear as soon as the enabled shops respond. Each shop request has a timeout, and the UI shows which shops returned offers and errors for shops it could not search. Store search pages vary, so a retailer may need a dedicated adapter if its site does not expose a compatible public search endpoint or product markup.

## Features

- Search shop listings and sort by price or retailer.
- Open the original listing on the seller site.
- See reported availability and the time each retailer responded.
- Inspect 27 configured store candidates; disabled candidates are not treated as confirmed sources.
- Website users can save searches and price targets locally in their browser.
- Android users search stores directly from their phone; no server, computer, or account is needed.
- The backend exposes health, shop status, products, offers, and price-history APIs.

## Build the Android app

Open `android/` in Android Studio with JDK 17 and Android SDK Platform 36 installed. This project uses Gradle 9.3.1 and omits generated Gradle wrapper binaries. With Gradle 9.3.1 installed, build with:

```sh
gradle -p android assembleDebug
```

The output is `android/app/build/outputs/apk/debug/app-debug.apk`. Google Play publication requires a separately managed release signing key and Play Console setup. Never commit signing keys or passwords.

## Run the website backend

On Windows, double-click `start.bat`; on macOS/Linux, run `sh ./start.sh`. Or run `node server/server.js`. Use `Ctrl+C` to stop it. `collect.bat` or `npm run collect` starts a manual refresh.

The live search adapter tries WooCommerce Store API search, Shopify product suggestions, retailer search pages, JSON-LD product data, and common product-card markup. It does not use `robots.txt` as a blocker for retailers the project is authorized to query. It reports real access errors and does not bypass logins, CAPTCHAs, or anti-bot protections. The old full-catalogue collector is only an optional legacy utility; it is not run by normal website searches. Set `AUTO_COLLECT=true` to opt into that legacy utility.

## Repository layout

```text
web/                         responsive Albanian website
server/server.js             static server and JSON API
server/collector.js          live retailer search adapters; legacy collector entry point
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

Set `PORT` to change the web server port. Live searches do not use or update `server/db.json`. The legacy collection is disabled by default; `AUTO_COLLECT=true` opts into it.

## License

MIT. See [LICENSE](LICENSE).




