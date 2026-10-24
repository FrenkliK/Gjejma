# Gjejma 🇦🇱

**Gjej çmimin. Jo xhiron.** Gjejma is an open-source Albanian price comparison project. Search once and see offers collected from Albanian-market shops, then buy directly from the retailer. Gjejma does not sell products or handle checkout.

This repository contains the website, Node.js API and collector, and a native Android app. The website and Android app use the same API and offer database.

## Start quickly on Windows

1. Install [Node.js 20 or newer](https://nodejs.org/). No `npm install` is needed; the server uses Node built-ins.
2. Download or clone this repository.
3. Double-click `start.bat`.
4. The website opens at `http://localhost:3000`. The server attempts to collect public retailer catalogues on startup and then every six hours.
5. Search for a product. The first catalogue refresh may take a few minutes. If a shop blocks automated access or has no supported public feed, it will be skipped and its error recorded.

On macOS/Linux, install Node.js 20+, open a terminal in the repository, and run:

```sh
sh ./start.sh
```

Or run `node server/server.js` on any platform. Use `Ctrl+C` to stop the server. `collect.bat` or `npm run collect` manually starts a collection while the server is already running.

**No fake offers are bundled.** The initial database is empty. Search results appear only after the collector successfully gets product data from retailers. Actual coverage depends on network access, each shop's current robots policy, available public product data, and terms of use.

## What works

- Search offers by product words, sorted by price or retailer.
- Open the original product listing on the seller's own site.
- See the reported availability and time the offer was collected.
- View the 27-store directory with enabled/disabled and last-run status from the API.
- Save searches locally in the browser.
- Save a local price target. While the website is open it checks saved targets every five minutes, and can show a browser notification if permission is granted. This is a browser-local alert, not a server email or an Android background alert.
- Review health, shop status, product records and price history through the JSON API.
- Use the Android client to search the same server catalogue and open offers in the browser.

“Real time” does not mean that Gjejma can guarantee a live checkout price. The collector refreshes on a schedule. A retailer may change price or stock between refreshes, so check the retailer's page before buying.

## Android app

The native Android project lives in `android/`. It uses Kotlin and Jetpack Compose and connects to a Gjejma server. It does not include its own server or product catalogue.

### Easiest way to get an APK

After this repository is pushed to GitHub, open **Actions → Android app**. Each successful build uploads a `gjejma-android-debug` APK artifact for 30 days. Download it, extract the APK, and install it on an Android device. Android may ask you to allow installation from the browser or file manager. Debug APKs are for testing and sideloading, not Google Play distribution.

The Android workflow also runs on pull requests that change `android/`.

After pushing the repository, you can create a permanent GitHub Release APK by pushing a version tag:

```sh
git tag v1.0.0
git push origin v1.0.0
```

The `Android release APK` workflow builds the app and attaches a debug-signed APK to that Release. This is suitable for testing and sideloading; Play Store publication requires a separately signed release build.

### Build in Android Studio

1. Install Android Studio, JDK 17, and Android SDK Platform 37.
2. Use Gradle 9.3.1 (required by the configured Android Gradle Plugin). This repository does not include the Gradle wrapper. If Android Studio asks for a Gradle distribution, select/download Gradle 9.3.1. Alternatively install Gradle 9.3.1 and run commands from the repository root.
3. Open the `android/` folder as a project and sync dependencies.
4. Select the `app` run configuration and run it on an emulator or device. To build a debug APK from a terminal, run `gradle -p android assembleDebug`; the APK is created at `android/app/build/outputs/apk/debug/app-debug.apk`.

The repository does not contain signing keys. Publishing to Google Play requires a release signing key and Play Console setup; never commit the key or its passwords.

### Connect Android to the backend

The app starts with `http://10.0.2.2:3000`, which is the Android Emulator's address for a server running on the same computer. On a physical phone:

1. Connect the phone and computer to the same Wi-Fi network.
2. Start Gjejma on the computer.
3. Find the computer's private LAN IP, such as `192.168.1.23`.
4. In the app's server field, enter `http://192.168.1.23:3000` and search.
5. If it cannot connect, allow Node.js through the computer's firewall and check that the phone can access the computer on port 3000.

For use away from the home network, deploy the Node server to a public host with HTTPS, then enter that server address in the app. Do not use plain HTTP over public Wi-Fi.

## Repository layout

```text
web/                         responsive Albanian website
server/server.js             static server and JSON API
server/collector.js          scheduled retailer collector
server/stores.json            retailer directory and adapter settings
server/db.json.example        empty database template; runtime data stays local
android/                      native Android Studio project
docs/ARCHITECTURE.md          components, data model and API details
.github/workflows/android.yml CI build that publishes a downloadable APK artifact
start.bat / start.sh          local launch shortcuts
collect.bat                   manual catalogue refresh shortcut on Windows
```

## How the collector works

The server runs the collector at startup and then every six hours by default. The collector processes enabled sources one at a time, uses request timeouts and delays, checks `robots.txt`, and supports:

1. The public WooCommerce Store API for stores configured with `woocommerce`.
2. Product JSON-LD on a public product page as a fallback.

The current adapters are deliberately generic. A store can still fail if its feed is unavailable, the site blocks automated requests, its product pages need JavaScript/login, its JSON shape differs, or its access terms do not permit collection. Failures are recorded per shop; the rest continue. There is no CAPTCHA or login bypass and no rotating proxy.

Only enable a store after checking that it is an Albanian-market retailer or fulfils orders in Albania, that the public data source works, and that its robots rules and terms permit the intended use. The directory includes candidates, not a promise that every listed shop currently has an integration. Molla Stores is in North Macedonia and is disabled by default until Albanian fulfilment is verified.

An offer keeps its own shop listing identity (SKU when available, otherwise product URL). Similar names are not automatically claimed to be identical products. Storage size, color, region, condition, bundle and warranty can change a product match. Gjejma currently exposes those listings as search results; robust cross-retailer matching by GTIN/EAN/MPN is future work.

## Configuration

The project runs with Node.js alone and has no runtime npm dependencies. Optional environment variables:

| Variable | Default | Purpose |
|---|---:|---|
| `PORT` | `3000` | HTTP server port |
| `AUTO_COLLECT` | `true` | Set to `false` to disable automatic collection |
| `COLLECT_INTERVAL_MINUTES` | `360` | Collection interval; values below 5 are reset to 360 |

Example, disabling collection while examining an existing database:

```powershell
$env:AUTO_COLLECT = "false"
$env:PORT = "3000"
node server/server.js
```

The collector creates and writes `server/db.json` after the first retailer run. This working database is ignored by Git so collected product data and history do not accidentally enter commits. `server/db.json.example` documents its empty starting shape. Writes are atomic, and a file lock prevents overlapping collector processes. Back up the runtime database if you care about local price history. For a public deployment, JSON-file writes are a small-installation starter, not a high-concurrency production database; use SQLite or PostgreSQL for a growing shared service and provide persistent storage.

## API reference

The API is served by the same origin as the site:

| Method and path | Description |
|---|---|
| `GET /api/health` | Server status, catalogue counts and latest run |
| `GET /api/shops` | Shop registry with enabled state, last check, result count and run state |
| `GET /api/search?q=iphone%2018` | Token search and collected offers |
| `GET /api/products?limit=100&offset=0&q=iphone` | Paginated product catalogue |
| `GET /api/products/:id` | Product listing, offers and recorded price history |

Example:

```sh
curl "http://localhost:3000/api/search?q=iphone%2018%20pro"
```

Offer availability values are `in_stock`, `out_of_stock`, `preorder`, or `unknown`. `unknown` must not be interpreted as in stock. Prices retain the source currency; the Albanian lek is displayed as `L`.

## Hosting it for everyone

The local setup is a personal server: each person who runs it has their own catalogue and update job. To provide one common, fresh catalogue to everyone, deploy `server/` and `web/` together on a Node host that provides:

- A persistent writable disk or managed database for offers and price history.
- A process that stays running for the HTTP server and its scheduled collection.
- A public HTTPS URL and regular backups.
- Monitoring for failed collections and source changes.

Set `PORT` to the host-provided port. The server serves the frontend and API from one origin. Point the Android app at the HTTPS URL. Do not deploy this project as a static-only site; static hosting cannot run the collector or API.

## Development

```sh
npm start       # run the server and scheduled collector
npm run collect # run one manual collection alongside the server
npm run check   # syntax-check the JavaScript files
```

Add or edit sources in `server/stores.json`. Before setting `enabled` to `true`, confirm the domain, local delivery, access permission, adapter type, and currency. Add a store-specific adapter when the shared WooCommerce/JSON-LD logic is unsuitable; do not make requests more frequent to work around rate limits. Keep credentials out of the repository.

See [Architecture](docs/ARCHITECTURE.md) for API structures and internal data flow. Contributions and store suggestions are welcome through GitHub issues and pull requests.

## Privacy and security

- Gjejma does not require an account.
- Search requests are processed by the server but are not saved as a user history.
- Saved searches and price targets remain in the browser's local storage.
- The collector stores public listing data, not customer data.
- The local server has no authentication; do not expose it directly to the public internet without adding authentication, rate limits, TLS and operational safeguards.
- The Android app needs internet permission to reach the API and retailer product pages.

## Known limitations

- There is no guaranteed live or complete coverage across all listed shops.
- The first collection can take time and may produce zero offers if network access or retailer access rules prevent collection.
- Product variants are not yet matched confidently across different shops.
- JSON database files are not suitable for large concurrent public traffic.
- Browser price alerts run only while the web page is open; Android background alerts and email alerts are not implemented.
- An APK becomes available as a GitHub Actions artifact after the repository is published and its Android workflow completes; tagged versions are attached to Releases.

## License

MIT. See [LICENSE](LICENSE).
