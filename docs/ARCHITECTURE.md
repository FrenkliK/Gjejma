# Architecture

Gjejma has a live-search website and a standalone Android client:

```text
local Node.js server
  ├── serves web/ static site
  ├── exposes live /api/search plus /api/shops and /api/health
  └── queries matching retailers per search; offer results are not persisted
          ↑
       web/app.js

Android Compose APK
  └── bundles the retailer directory and search config, then queries stores directly
```

The website server binds to the machine's available interfaces. The Android APK does not use the Node server and works over the phone's own internet connection.

## Search and product identity

`search-config.json` defines aliases, automatic category keywords, user-selectable category filters, and retailer groups. The website and Android app offer an all-categories option plus filters such as phones, consoles, games and accessories, TVs and audio, large and small appliances, climate, personal care, computers, mobility, and home. A selected filter narrows both the retailers checked and returned product titles. For example, `Samsung` with `phones` excludes TV and appliance listings; `PS5` with `consoles` excludes game and accessory listings. Without a selected filter, existing broad search behavior remains. Queries normalize punctuation and accents, drop intent words such as “price” or “buy,” expand abbreviations such as `PS5` to PlayStation 5, and allow a one-character typo when matching returned titles. Search results are only held in memory for that response. Product variants remain separate offers; Gjejma does not assert that similar titles are exactly the same product.

## Collection

Normal searches use `collector.js` live adapters for WooCommerce, Shopify, Shopware, Neptun's public product-autocomplete API, retailer search pages, JSON-LD, and common product cards. Neptun search results include its product title, current price, stock flag, and product URL; the Android client uses the same endpoint directly. `robots.txt` is not used as a blocker for stores the project is authorized to query. Actual HTTP failures are shown per retailer; the app does not bypass logins, CAPTCHAs, or anti-bot protections. A legacy full-catalogue collector is available as an opt-in utility with `AUTO_COLLECT=true`, but normal search does not start it or read its database.

Retailer layouts and search APIs vary. A store may need a dedicated adapter if its search is JavaScript-only, changes its endpoint, or returns a real access error. Category routing reduces unnecessary requests, but does not guarantee every enabled store can return a result for every product.

## Data records

- Live offer: observed title, retailer, price, currency, reported availability, product URL, and request time.
- Store: retailer URL, adapter, currency, enabled state, and category tags used for routing.

`db.json` remains for the opt-in legacy collector and catalogue endpoints; live search does not write query results to it.

## API

- `GET /api/health` — service and legacy catalogue counts.
- `GET /api/shops` — shop list, enabled state, and category tags.
- `GET /api/categories` — user-facing filter labels and product-title include/exclude rules.
- `GET /api/search?q=ps5` — live offers, detected category, checked-store counts, and per-store errors.
- `GET /api/search?q=samsung&category=phones` — live offers narrowed to the selected category.
- `GET /api/products?limit=100&offset=0&q=iphone` — catalogue page.
- `GET /api/products/:id` — one listing, current offers and recorded history.

Responses are JSON. There is no account system or user search history on the server. Browser saved searches and local price targets stay in that browser's local storage.

