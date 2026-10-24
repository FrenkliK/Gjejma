# Architecture

Gjejma has three parts that share a small JSON API:

```text
local/hosted Node.js server
  ├── serves web/ static site
  ├── exposes /api/search, /api/products, /api/shops and /api/health
  ├── stores normalized offers and price changes in server/db.json
  └── starts server/collector.js at launch and on a timer
          ↑                         ↑
       web/app.js          Android Compose client
                                  (user provides server URL)
```

The backend binds to the machine's available interfaces so a phone on the same Wi-Fi can reach it. A hosted server should use HTTPS. Local phone access uses the computer's LAN IP and may require allowing Node through the computer's firewall.

## Search and product identity

Search token-matches product title/model. Each source listing keeps its own identity based on the shop plus SKU (or product URL), avoiding accidental merging of different colors, storage sizes, conditions, warranties, and regions. As a result, search can show separate variant listings and does not yet assert that similar titles are exactly the same product. A future catalogue matcher should use GTIN/EAN/MPN where available and retain an auditable match reason.

## Collection

`collector.js` processes enabled stores in order, with request timeouts and pauses. It checks `robots.txt`, tries the WooCommerce Store API for configured WooCommerce adapters, and otherwise extracts public Product JSON-LD. Each store's outcome is recorded in `runs`; one failed retailer does not abort the remaining shops. Automatic collection runs immediately when the server starts unless `AUTO_COLLECT=false`, then every six hours by default. Set `COLLECT_INTERVAL_MINUTES` to a value of at least five to change the cadence. A filesystem lock prevents overlapping collection processes, and JSON writes use a temporary file followed by a rename.

This approach is intentionally conservative and best-effort. Public access does not automatically grant permission to reuse data. Review each retailer's terms, robots policy, endpoint behavior, and local delivery before enabling it. No bot protection is bypassed. A site that requires login, blocks automated requests, has no supported structured source, or asks for contact is not collected automatically.

## Data records

- Product: stable listing ID, observed title/model, last update time.
- Offer: product ID, shop, price, currency, availability state, product URL, and `checkedAt`.
- History: prior price/availability values when an offer changes.
- Run: per-shop success/error, count, and start/finish timestamps.

`db.json` is a lightweight starter store, not a high-concurrency production database. Back it up. For a larger public service, replace it with SQLite or PostgreSQL, add transactional writes, migrations, alert queues and monitoring.

## API

- `GET /api/health` — service and catalogue counts.
- `GET /api/shops` — shop list, enabled state, last run and offer count.
- `GET /api/search?q=iphone%2018` — title/token search and active offers sorted by best price per listing.
- `GET /api/products?limit=100&offset=0&q=iphone` — catalogue page.
- `GET /api/products/:id` — one listing, current offers and recorded history.

Responses are JSON. There is no account system or user search history on the server. Browser saved searches and local price targets stay in that browser's local storage.
