const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const stores = require('./stores.json');
const DB = path.join(__dirname, 'db.json');
const TIMEOUT = 12000;
const USER_AGENT = 'Gjejma/1.1 (Albanian price comparison; https://github.com/FrenkliK/Gjejma)';

function normalize(value = '') {
  return String(value).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
}

function decode(value = '') {
  return String(value).replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&#39;|&apos;/g, "'")
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(+n))
    .replace(/&#x([\da-f]+);/gi, (_, n) => String.fromCodePoint(parseInt(n, 16)))
    .replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
}

function parsePrice(value) {
  if (value == null) return null;
  const raw = String(value).replace(/[^\d.,]/g, '');
  if (!raw) return null;
  const normalized = raw.includes(',') && raw.includes('.')
    ? raw.lastIndexOf(',') > raw.lastIndexOf('.') ? raw.replace(/\./g, '').replace(',', '.') : raw.replace(/,/g, '')
    : (raw.match(/,/g) || []).length === 1 && raw.split(',')[1].length <= 2 ? raw.replace(',', '.') : raw.replace(/[,.]/g, '');
  const number = Number(normalized);
  return Number.isFinite(number) && number > 0 ? number : null;
}

async function fetchText(address) {
  const response = await fetch(address, {
    signal: AbortSignal.timeout(TIMEOUT),
    headers: { 'user-agent': USER_AGENT, accept: 'application/json,text/html;q=0.9,*/*;q=0.8' },
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.text();
}

function productRow(store, { title, url, price, currency, availability, sku }) {
  title = String(title || '').trim();
  const amount = parsePrice(price);
  if (!title || !url || !amount) return null;
  let link;
  try { link = new URL(url, `https://${store.domain}`); } catch { return null; }
  if (link.hostname !== store.domain && !link.hostname.endsWith(`.${store.domain}`)) return null;
  const stock = String(availability || '').toLowerCase();
  const state = /out|unavailable|sold|false/.test(stock) ? 'out_of_stock'
    : /instock|in stock|available|true/.test(stock) ? 'in_stock' : 'unknown';
  return {
    title, url: link.href, price: amount, currency: currency || store.currency || 'ALL',
    availability: state, sku: String(sku || ''), storeId: store.id, store: store.name, domain: store.domain,
    mark: store.name.split(/\s+/).map(part => part[0]).join('').slice(0, 3).toUpperCase(), checkedAt: new Date().toISOString(),
  };
}

function fromWoo(raw, store) {
  let rows;
  try { rows = JSON.parse(raw); } catch { return []; }
  if (!Array.isArray(rows)) return [];
  return rows.map(p => {
    const prices = p.prices || {};
    let amount = prices.price ?? p.sale_price ?? p.price ?? p.regular_price;
    amount = parsePrice(amount);
    if (amount && prices.price != null) amount /= 10 ** Number(prices.currency_minor_unit || 0);
    return productRow(store, {
      title: p.name || p.title, url: p.permalink || p.url, price: amount,
      currency: prices.currency_code || p.currency || store.currency,
      availability: p.stock_status || p.availability, sku: p.sku || p.id,
    });
  }).filter(Boolean);
}

function fromShopify(raw, store) {
  let data;
  try { data = JSON.parse(raw); } catch { return []; }
  const products = data?.resources?.results?.products || data?.products || [];
  return products.map(p => {
    const variant = p.variants?.[0] || {};
    return productRow(store, {
      title: p.title, url: p.url || p.handle && `/products/${p.handle}`,
      price: variant.price || p.price, currency: p.currency || store.currency,
      availability: variant.available ? 'in_stock' : 'unknown', sku: variant.sku || p.id,
    });
  }).filter(Boolean);
}

function fromJsonLd(html, store) {
  const output = [];
  for (const match of html.matchAll(/<script[^>]+type=["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi)) {
    try {
      const root = JSON.parse(match[1]);
      const nodes = Array.isArray(root) ? root : [...(root['@graph'] || []), root];
      for (const node of nodes) {
        const types = Array.isArray(node?.['@type']) ? node['@type'] : [node?.['@type']];
        if (!types.includes('Product')) continue;
        const offer = Array.isArray(node.offers) ? node.offers[0] : node.offers;
        if (!offer) continue;
        const row = productRow(store, {
          title: node.name, url: node.url || offer.url, price: offer.price || offer.lowPrice,
          currency: offer.priceCurrency, availability: offer.availability, sku: node.sku,
        });
        if (row) output.push(row);
      }
    } catch { /* Ignore malformed structured data on a page. */ }
  }
  return output;
}

function fromHtmlCards(html, store) {
  const output = [];
  const blocks = html.match(/<(?:article|li|div)\b[^>]*(?:product|item)[^>]*>[\s\S]*?<\/(?:article|li|div)>/gi) || [];
  for (const block of blocks) {
    const anchor = block.match(/<a\b[^>]*href=["']([^"']+)["'][^>]*>/i);
    const titleTag = block.match(/<(?:h2|h3|[^>]+(?:product-title|product_title|woocommerce-loop-product__title)[^>]*)[^>]*>([\s\S]*?)<\/(?:h2|h3|[^>]+)>/i);
    const titleAttr = block.match(/(?:title|aria-label)=["']([^"']+)["']/i);
    const priceMatch = block.match(/(?:woocommerce-Price-amount|price|amount)[^>]*>[\s\S]{0,100}?([\d][\d.,\s]*)/i);
    const rawPrice = priceMatch?.[1] || block.match(/(?:ALL|Lek|L)\s*([\d][\d.,\s]*)/i)?.[1] || block.match(/([\d][\d.,\s]*)\s*(?:ALL|Lek|L)\b/i)?.[1];
    const row = productRow(store, {
      title: decode(titleTag?.[1] || titleAttr?.[1]), url: anchor?.[1], price: rawPrice,
      availability: /out of stock|sold out|nuk ka stok/i.test(block) ? 'out_of_stock' : /in stock|ne stok/i.test(block) ? 'in_stock' : 'unknown',
    });
    if (row) output.push(row);
  }
  return output;
}

function queryMatches(title, query) {
  const terms = normalize(query).split(/\s+/).filter(Boolean);
  const normalizedTitle = normalize(title);
  return terms.length > 0 && terms.every(term => normalizedTitle.includes(term));
}

async function searchStore(store, query) {
  const encoded = encodeURIComponent(query);
  const errors = [];
  const attempts = [];
  if (store.adapter === 'shopify') {
    attempts.push({ url: `https://${store.domain}/search/suggest.json?q=${encoded}&resources[type]=product&resources[limit]=20`, parse: fromShopify });
  }
  attempts.push({ url: `https://${store.domain}/wp-json/wc/store/v1/products?search=${encoded}&per_page=30`, parse: fromWoo });
  attempts.push({ url: `https://${store.domain}/?s=${encoded}&post_type=product`, parse: (body, s) => [...fromJsonLd(body, s), ...fromHtmlCards(body, s)] });
  attempts.push({ url: `${store.url}${store.url.includes('?') ? '&' : '?'}s=${encoded}&post_type=product`, parse: (body, s) => [...fromJsonLd(body, s), ...fromHtmlCards(body, s)] });
  attempts.push({ url: `https://${store.domain}/search?q=${encoded}`, parse: (body, s) => [...fromJsonLd(body, s), ...fromHtmlCards(body, s)] });

  for (const attempt of attempts) {
    try {
      const body = await fetchText(attempt.url);
      const found = attempt.parse(body, store).filter(row => queryMatches(row.title, query));
      if (found.length) return { offers: found, error: null };
    } catch (error) { errors.push(error.message || 'request failed'); }
  }
  const distinct = [...new Set(errors)];
  return { offers: [], error: distinct.length ? distinct.join(', ') : 'No matching public product listing found' };
}

async function searchStores(query) {
  const selected = stores.filter(store => store.enabled && store.country === 'AL');
  const checkedAt = new Date().toISOString();
  const outcomes = await Promise.all(selected.map(async store => {
    try { return { store, ...(await searchStore(store, query)) }; }
    catch (error) { return { store, offers: [], error: error.message || 'request failed' }; }
  }));
  const offers = outcomes.flatMap(outcome => outcome.offers);
  const storeErrors = outcomes.filter(outcome => outcome.error).map(({ store, error }) => ({ id: store.id, name: store.name, error }));
  offers.sort((a, b) => a.price - b.price);
  return { query, total: offers.length, offers, storesChecked: selected.length, storesWithOffers: outcomes.filter(o => o.offers.length).length, storeErrors, checkedAt };
}

// Legacy opt-in catalogue collection remains available for maintainers. Normal searches never read or write this data.
function legacyRead() { try { return JSON.parse(fs.readFileSync(DB, 'utf8')); } catch { return { products: [], offers: [], history: [], runs: [] }; } }
function legacySave(state) { fs.writeFileSync(DB, JSON.stringify(state, null, 2)); }
function id(value) { return crypto.createHash('sha1').update(value).digest('hex').slice(0, 16); }
async function legacyCollect() {
  const state = legacyRead();
  for (const store of stores.filter(s => s.enabled && s.adapter === 'woocommerce')) {
    try {
      const raw = await fetchText(`https://${store.domain}/wp-json/wc/store/v1/products?per_page=100`);
      for (const row of fromWoo(raw, store)) {
        const productId = id(row.sku ? `${store.id}:${row.sku}` : `${store.id}:${row.url}`);
        if (!state.products.some(p => p.id === productId)) state.products.push({ id: productId, title: row.title, model: row.title, updatedAt: row.checkedAt });
        const offer = { id: id(`${productId}:${store.id}`), productId, ...row };
        const previous = state.offers.findIndex(o => o.id === offer.id);
        if (previous < 0) state.offers.push(offer); else state.offers[previous] = offer;
      }
      console.log(`${store.name}: legacy feed processed`);
    } catch (error) { console.log(`${store.name}: ${error.message}`); }
  }
  legacySave(state);
}

if (require.main === module) legacyCollect().catch(error => { console.error(error); process.exitCode = 1; });
module.exports = { searchStores };

