package al.gjejma.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

private val Forest = Color(0xFF168458)
private val Ink = Color(0xFF17221E)
private val Muted = Color(0xFF718078)
private val Page = Color(0xFFF5F7F5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Page) {
                    GjejmaScreen(applicationContext) { link ->
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                    }
                }
            }
        }
    }
}

private data class Store(val id: String, val name: String, val domain: String, val url: String, val adapter: String, val currency: String, val categories: Set<String>)
private data class Offer(val store: String, val domain: String, val price: Double, val currency: String, val availability: String, val url: String, val checkedAt: Long)
private data class StorePlan(val category: String?, val filterCategory: String?, val stores: List<Store>)
private suspend fun fetchText(address: String): String = withContext(Dispatchers.IO) {
    val c = URL(address).openConnection() as HttpURLConnection
    c.connectTimeout = 10_000; c.readTimeout = 16_000; c.instanceFollowRedirects = true
    c.setRequestProperty("User-Agent", "GjejmaAndroid/1.0 (+https://github.com/FrenkliK/Gjejma; public product catalog)")
    c.setRequestProperty("Accept", "application/json,text/html;q=0.9,*/*;q=0.8")
    try { if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}"); c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
}

private suspend fun fetchNeptunSearch(store: Store, term: String): String = withContext(Dispatchers.IO) {
    val origin = URL(store.url).let { "${it.protocol}://${it.host}" }
    val c = URL(URL(store.url), "/Product/SearchProductsAutocomplete").openConnection() as HttpURLConnection
    c.connectTimeout = 10_000; c.readTimeout = 16_000; c.instanceFollowRedirects = true
    c.requestMethod = "POST"; c.doOutput = true
    c.setRequestProperty("User-Agent", "GjejmaAndroid/1.0 (+https://github.com/FrenkliK/Gjejma; public product catalog)")
    c.setRequestProperty("Accept", "application/json, text/plain, */*")
    c.setRequestProperty("Content-Type", "application/json;charset=UTF-8")
    c.setRequestProperty("Origin", origin); c.setRequestProperty("Referer", "$origin/")
    c.setRequestProperty("FROM-ANGULAR", "true"); c.setRequestProperty("X-Requested-With", "XMLHttpRequest")
    val body = JSONObject().put("term", term).put("page", 1).put("itemsPerPage", 200).toString().toByteArray(Charsets.UTF_8)
    try {
        c.outputStream.use { it.write(body) }
        if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
        c.inputStream.bufferedReader().use { it.readText() }
    } finally { c.disconnect() }
}

private suspend fun storeDirectory(context: android.content.Context): List<Store> = withContext(Dispatchers.IO) {
    val raw = context.assets.open("stores.json").bufferedReader().use { it.readText() }
    val a = JSONArray(raw)
    buildList {
        for (i in 0 until a.length()) {
            val s = a.getJSONObject(i)
            if (s.optBoolean("enabled") && s.optString("country") == "AL") {
                val tags=s.optJSONArray("categories") ?: JSONArray()
                add(Store(s.getString("id"), s.getString("name"), s.getString("domain"), s.getString("url"), s.optString("adapter", "jsonld"), s.optString("currency", "ALL"), buildSet { for (j in 0 until tags.length()) add(tags.getString(j)) }))
            }
        }
    }
}

private fun readSearchConfig(context: android.content.Context) = JSONObject(context.assets.open("search-config.json").bufferedReader().use { it.readText() })
private suspend fun loadSearchConfig(context: android.content.Context): JSONObject = withContext(Dispatchers.IO) { readSearchConfig(context) }

private fun parsePrice(raw: Any?): Double? {
    val s = raw?.toString()?.replace(Regex("[^0-9.,]"), "")?.takeIf(String::isNotBlank) ?: return null
    val normalized = when {
        s.contains(',') && s.contains('.') -> if (s.lastIndexOf(',') > s.lastIndexOf('.')) s.replace(".", "").replace(',', '.') else s.replace(",", "")
        s.count { it == ',' } == 1 && s.substringAfter(',').length <= 2 -> s.replace(',', '.')
        else -> s.replace(",", "")
    }
    return normalized.toDoubleOrNull()?.takeIf { it > 0 }
}

private fun safeProduct(store: Store, title: String, link: String, price: Double, currency: String, stock: String, identity: String): JSONObject? {
    if (title.isBlank() || link.isBlank()) return null
    val absoluteLink = if (link.startsWith("http://") || link.startsWith("https://")) link else "https://${store.domain}/${link.removePrefix("/")}"
    val uri = runCatching { Uri.parse(absoluteLink) }.getOrNull() ?: return null
    val host = uri.host ?: return null
    if (host != store.domain && !host.endsWith(".${store.domain}")) return null
    val availability = when {
        stock.contains("out", true) || stock.contains("sold", true) -> "out_of_stock"
        stock.contains("instock", true) || stock.contains("in stock", true) || stock.contains("available", true) -> "in_stock"
        else -> "unknown"
    }
    val offer = JSONObject().put("store", store.name).put("domain", store.domain).put("price", price).put("currency", currency).put("availability", availability).put("url", uri.toString()).put("checkedAt", System.currentTimeMillis())
    return JSONObject().put("key", "${store.id}:$identity").put("title", title).put("offer", offer)
}

private fun normalize(s: String) = java.text.Normalizer.normalize(s.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("\\bbiciklet\\b"), "biciklete").replace(Regex("[^a-z0-9]+"), " ").trim()

private fun hasPhrase(text: String, phrase: String): Boolean {
    val words = normalize(text).split(" ").filter(String::isNotBlank)
    val phraseWords = normalize(phrase).split(" ").filter(String::isNotBlank)
    if (phraseWords.isEmpty()) return false
    if (" ${words.joinToString(" ")} ".contains(" ${phraseWords.joinToString(" ")} ")) return true
    if (words.size < phraseWords.size) return false
    for (start in 0..words.size - phraseWords.size) {
        val prefixMatches = phraseWords.dropLast(1).indices.all { offset -> words[start + offset] == phraseWords[offset] }
        val last = words[start + phraseWords.size - 1]
        val prefix = phraseWords.last()
        val suffix = last.removePrefix(prefix)
        if (prefixMatches && last.startsWith(prefix) && suffix.firstOrNull()?.isDigit() == true) return true
    }
    return false
}

private fun isOneTypoAway(a: String, b: String): Boolean {
    if (a.length < 5 || b.length < 5 || kotlin.math.abs(a.length - b.length) > 1) return false
    var i=0; var j=0; var edits=0
    while (i<a.length && j<b.length) {
        if (a[i]==b[j]) { i++; j++; continue }
        if (++edits>1) return false
        if (a.length>b.length) i++ else if (b.length>a.length) j++ else { i++; j++ }
    }
    return (edits + if (i<a.length || j<b.length) 1 else 0) <= 1
}

private fun correctedQuery(query: String, config: JSONObject): String {
    val vocabulary = mutableSetOf<String>()
    val categories = config.optJSONObject("categories") ?: JSONObject()
    val categoryKeys = categories.keys()
    while (categoryKeys.hasNext()) {
        val words = categories.optJSONArray(categoryKeys.next()) ?: continue
        for (i in 0 until words.length()) vocabulary += normalize(words.optString(i)).split(" ").filter(String::isNotBlank)
    }
    val aliases = config.optJSONArray("aliases") ?: JSONArray()
    for (i in 0 until aliases.length()) {
        val alias=aliases.optJSONObject(i) ?: continue
        for (field in listOf("match", "search")) {
            val words=alias.optJSONArray(field) ?: continue
            for (j in 0 until words.length()) vocabulary += normalize(words.optString(j)).split(" ").filter(String::isNotBlank)
        }
    }
    val stopwords = config.optJSONArray("stopwords") ?: JSONArray()
    val ignored=(0 until stopwords.length()).map { normalize(stopwords.optString(it)) }.toSet()
    return normalize(query).split(" ").filter { it.isNotBlank() && it !in ignored }.map { token ->
        if (token.length < 5 || token in vocabulary) token
        else vocabulary.filter { isOneTypoAway(token, it) }.singleOrNull() ?: token
    }.joinToString(" ")
}

private fun detectCategory(query: String, config: JSONObject): String? {
    val corrected=correctedQuery(query,config)
    val aliases = config.optJSONArray("aliases") ?: JSONArray()
    for (i in 0 until aliases.length()) {
        val alias = aliases.optJSONObject(i) ?: continue
        val matches = alias.optJSONArray("match") ?: continue
        if ((0 until matches.length()).any { hasPhrase(query, matches.optString(it)) || hasPhrase(corrected, matches.optString(it)) }) return alias.optString("category").takeIf(String::isNotBlank)
    }
    val categories = config.optJSONObject("categories") ?: return null
    val keys = categories.keys()
    while (keys.hasNext()) {
        val category = keys.next(); val words = categories.optJSONArray(category) ?: continue
        if ((0 until words.length()).any { hasPhrase(query, words.optString(it)) || hasPhrase(corrected, words.optString(it)) }) return category
    }
    return null
}

private fun searchAlternatives(query: String, config: JSONObject): List<String> {
    val stopwords = config.optJSONArray("stopwords") ?: JSONArray()
    val ignored = (0 until stopwords.length()).map { normalize(stopwords.optString(it)) }.toSet()
    val clean = normalize(query).split(" ").filter { it.isNotBlank() && it !in ignored }.joinToString(" ")
    val translated = clean.lowercase(Locale.ROOT)
        .replace(Regex("\\b(televisions?|smart tvs?)\\b"), "televizor")
        .replace(Regex("\\b(fridges?|friges?|refrigerators?|freezers?)\\b"), "frigorifer")
        .replace(Regex("\\btoasters?\\b"), "tostjere")
        .replace(Regex("\\b(electric bikes?|e-bikes?|ebikes?|electric bicycles?)\\b"), "biciklete elektrike")
        .replace(Regex("\\b(bikes?|bicycles?)\\b"), "biciklete")
        .replace(Regex("\\b(electric scooters?|e-scooters?)\\b"), "skuter elektrik")
        .replace(Regex("\\bmicrowaves?\\b"), "mikrovale")
        .replace(Regex("\\b(washing machines?|washers?)\\b"), "lavatrice")
        .replace(Regex("\\bdishwashers?\\b"), "lavastovilje")
        .replace(Regex("\\b(vacuum cleaners?|vacuums?)\\b"), "fshese")
    val extras = mutableListOf<String>()
    val aliases = config.optJSONArray("aliases") ?: JSONArray()
    for (i in 0 until aliases.length()) {
        val alias = aliases.optJSONObject(i) ?: continue
        val matches = alias.optJSONArray("match") ?: continue
        if ((0 until matches.length()).any { hasPhrase(query, matches.optString(it)) }) {
            val searches = alias.optJSONArray("search") ?: continue
            for (j in 0 until searches.length()) extras += searches.optString(j)
        }
    }
    return (listOf(clean, correctedQuery(query,config), translated) + extras).distinct().filter(String::isNotBlank)
}

private fun selectStores(query: String, stores: List<Store>, config: JSONObject, requestedCategory: String = ""): StorePlan {
    val categories=config.optJSONObject("userCategories") ?: JSONObject()
    val filterCategory=requestedCategory.takeIf { it.isNotBlank() && categories.has(it) }
    val category=filterCategory ?: detectCategory(query, config) ?: return StorePlan(null,null,stores)
    val groupKey=if(filterCategory!=null) categories.optJSONObject(category)?.optString("storeGroup") ?: category else category
    val groups = config.optJSONObject("storeGroups")?.optJSONArray(groupKey) ?: return StorePlan(null,null,stores)
    val allowed = (0 until groups.length()).map(groups::getString).toSet()
    val selected = stores.filter { store -> store.categories.any(allowed::contains) }
    return if (selected.isEmpty()) StorePlan(null,null,stores) else StorePlan(category,filterCategory,selected)
}

private fun matchesUserCategory(title: String, category: String?, config: JSONObject): Boolean {
    if(category==null)return true
    val rule=config.optJSONObject("userCategories")?.optJSONObject(category) ?: return true
    val normalized=normalize(title)
    val excluded=rule.optJSONArray("exclude") ?: JSONArray()
    if((0 until excluded.length()).any { hasPhrase(normalized,excluded.optString(it)) })return false
    val included=rule.optJSONArray("include") ?: return true
    return (0 until included.length()).any { hasPhrase(normalized,included.optString(it)) }
}

private fun queryMatches(title: String, query: String, config: JSONObject, filterCategory: String?): Boolean {
    if(!matchesUserCategory(title,filterCategory,config))return false
    val titleText=normalize(title); val titleTokens=titleText.split(" ").filter(String::isNotBlank)
    return searchAlternatives(query, config).any { alternative ->
        val terms=normalize(alternative).split(" ").filter(String::isNotBlank)
        terms.isNotEmpty() && terms.all { term -> titleText.contains(term) || titleTokens.any { isOneTypoAway(term,it) } }
    }
}

private val categoryLabels = mapOf("phones" to "Telefona", "gaming" to "Gaming", "computers" to "Kompjutera", "tv" to "TV", "appliances" to "Elektroshtëpiake", "mobility" to "Biçikleta & skuterë", "home" to "Shtëpi")

private fun queryUrl(store: Store, query: String, route: String = ""): String {
    val encoded = java.net.URLEncoder.encode(query, "UTF-8")
    return when {
        store.adapter == "shopify" && route.isEmpty() -> "https://${store.domain}/search/suggest.json?q=$encoded&resources[type]=product&resources[limit]=20"
        route == "cscart" -> "https://${store.domain}/?dispatch=products.search&match=all&subcats=Y&pcode_from_q=Y&pshort=Y&pfull=Y&pname=Y&pkeywords=Y&search_performed=Y&q=$encoded"
        route == "woo" -> "https://${store.domain}/wp-json/wc/store/v1/products?search=$encoded&per_page=100"
        route == "wpsearch" -> "https://${store.domain}/?s=$encoded"
        route == "search" -> if (store.adapter == "shopware") "https://${store.domain}/search?search=$encoded" else "https://${store.domain}/search?q=$encoded"
        route == "shopsearch" -> "https://${store.domain}/search?search=$encoded"
        route == "query" -> "https://${store.domain}/?q=$encoded"
        route == "opencart" -> "https://${store.domain}/index.php?route=product/search&search=$encoded"
        route == "products" -> "https://${store.domain}/products?search=$encoded"
        route == "store" -> "${store.url}${if (store.url.contains('?')) '&' else '?'}s=$encoded&post_type=product"
        else -> "https://${store.domain}/?s=$encoded&post_type=product"
    }
}

private fun parseSearchResponse(store: Store, raw: String, query: String, config: JSONObject, filterCategory: String?): List<Pair<String, Offer>> {
    val found = mutableListOf<Pair<String, Offer>>()
    fun add(title: String, link: String, price: Double?, currency: String, stock: String, identity: String) {
        if (price == null || title.isBlank() || !queryMatches(title, query, config, filterCategory)) return
        val offer = safeProduct(store, title, link, price, currency, stock, identity) ?: return
        val o = offer.getJSONObject("offer")
        found += title to Offer(store.name, store.domain, o.getDouble("price"), o.getString("currency"), o.getString("availability"), o.getString("url"), o.getLong("checkedAt"))
    }
    if (store.adapter == "cscart") {
        val forms = Regex("<form\\b[^>]*name=[\"']product_form_(\\d+)[\"'][^>]*>[\\s\\S]*?</form>", RegexOption.IGNORE_CASE)
        for (match in forms.findAll(raw)) {
            val block = match.value
            val titleLink = Regex("<a\\b[^>]*class=[\"'][^\"']*\\bproduct-title\\b[^\"']*[\"'][^>]*>", RegexOption.IGNORE_CASE).find(block)?.value.orEmpty()
            val title = Regex("\\btitle=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(titleLink)?.groupValues?.get(1)
                ?: Regex("<img\\b[^>]*\\balt=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1).orEmpty()
            val link = Regex("\\bhref=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(titleLink)?.groupValues?.get(1).orEmpty()
            val amount = Regex("<span\\b[^>]*class=[\"'][^\"']*\\bty-price-num\\b[^\"']*[\"'][^>]*>([\\d., ]+)</span>", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)
            add(title.replace("&amp;", "&").replace("&quot;", "\""), link, parsePrice(amount), store.currency,
                when {
                    Regex("out of stock|sold out|nuk ka(?:\\s+ne)?\\s+stok", RegexOption.IGNORE_CASE).containsMatchIn(block) -> "out of stock"
                    Regex("in stock|ne stok|available|vetëm\\s+\\d+\\s+produkte?\\s+të\\s+mbetura", RegexOption.IGNORE_CASE).containsMatchIn(block) -> "in stock"
                    else -> ""
                }, match.groupValues[1])
        }
        return found.distinctBy { it.second.url }
    }
    if (store.adapter == "shopify" && raw.trimStart().startsWith('{')) {
        val root = runCatching { JSONObject(raw) }.getOrNull()
        val resources = root?.optJSONObject("resources")?.optJSONObject("results")
        val products = resources?.optJSONArray("products") ?: root?.optJSONArray("products")
        if (products != null) for (i in 0 until products.length()) {
            val p = products.optJSONObject(i) ?: continue; val variant = p.optJSONArray("variants")?.optJSONObject(0)
            val link = p.optString("url").ifBlank { "/products/${p.optString("handle")}" }
            add(p.optString("title"), link, parsePrice(variant?.opt("price") ?: p.opt("price")), store.currency, if (variant?.optBoolean("available") == true) "in stock" else "", variant?.optString("sku").orEmpty())
        }
    }
    if (raw.trimStart().startsWith('[')) {
        val array = runCatching { JSONArray(raw) }.getOrNull()
        if (array != null) for (i in 0 until array.length()) {
            val p = array.optJSONObject(i) ?: continue; val prices = p.optJSONObject("prices")
            var amount = parsePrice(prices?.opt("price") ?: p.opt("sale_price") ?: p.opt("price") ?: p.opt("regular_price"))
            if (amount != null && prices?.has("price") == true) amount /= Math.pow(10.0, prices.optInt("currency_minor_unit", 0).toDouble())
            add(p.optString("name", p.optString("title")), p.optString("permalink", p.optString("url")), amount, prices?.optString("currency_code", store.currency) ?: store.currency, p.optString("stock_status"), p.optString("sku", p.optString("id")))
        }
    } else {
        val script = Regex("<script[^>]+type=[\\\"']application/ld\\+json[\\\"'][^>]*>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE)
        for (match in script.findAll(raw)) {
            val root = runCatching { JSONObject(match.groupValues[1]) }.getOrNull() ?: continue
            val graph = root.optJSONArray("@graph") ?: JSONArray().put(root)
            for (i in 0 until graph.length()) {
                val p = graph.optJSONObject(i) ?: continue
                if (p.opt("@type")?.toString()?.contains("Product") != true) continue
                val offers = p.optJSONArray("offers")?.optJSONObject(0) ?: p.optJSONObject("offers") ?: continue
                add(p.optString("name"), p.optString("url").ifBlank { offers.optString("url", store.url) }, parsePrice(offers.opt("price") ?: offers.opt("lowPrice")), offers.optString("priceCurrency", store.currency), offers.optString("availability"), p.optString("sku"))
            }
        }
        val blocks = Regex("<(?:article|li|div)\\b[^>]*(?:product|item)[^>]*>[\\s\\S]*?</(?:article|li|div)>", RegexOption.IGNORE_CASE)
        for (match in blocks.findAll(raw)) {
            val block = match.value
            val link = Regex("<a\\b[^>]*href=[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1) ?: continue
            val title = Regex("<(?:h2|h3)[^>]*>([\\s\\S]*?)</(?:h2|h3)>", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.replace(Regex("<[^>]*>"), " ")?.trim().orEmpty()
            val amount = Regex("(?:woocommerce-Price-amount|price|amount)[^>]*>[\\s\\S]{0,100}?([\\d][\\d., ]*)", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)
                ?: Regex("(?:ALL|Lek|L)\\s*([\\d][\\d., ]*)", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)
            add(title, link, parsePrice(amount), store.currency, if (Regex("out of stock|sold out|nuk ka stok", RegexOption.IGNORE_CASE).containsMatchIn(block)) "out" else "", link)
        }
    }
    return found.distinctBy { it.second.url }
}

private fun parseNeptunResponse(store: Store, raw: String, query: String, config: JSONObject, filterCategory: String?): List<Pair<String, Offer>> {
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
    val products = root.optJSONArray("list") ?: root.optJSONObject("ProductsResult")?.optJSONArray("results") ?: JSONArray()
    val found = mutableListOf<Pair<String, Offer>>()
    for (i in 0 until products.length()) {
        val product = products.optJSONObject(i) ?: continue
        if (product.has("Type") && product.optInt("Type") != 1) continue
        val title = product.optString("Title", product.optString("ShortTitle"))
        if (title.isBlank() || !queryMatches(title, query, config, filterCategory)) continue
        val price = listOf("WebshopDiscountPrice", "ActualPrice", "DiscountPrice", "RegularPrice")
            .firstNotNullOfOrNull { parsePrice(product.opt(it)) } ?: continue
        val stock = if (!product.has("AvailableWebshop")) "" else if (product.optBoolean("AvailableWebshop")) "in stock" else "out of stock"
        val identity = product.optString("CodeNumber", product.optString("ProductCode", product.optString("Barcode", product.optString("Id"))))
        val parsed = safeProduct(store, title, product.optString("Link", product.optString("Url")), price, store.currency, stock, identity) ?: continue
        val offer = parsed.getJSONObject("offer")
        found += title to Offer(store.name, store.domain, offer.getDouble("price"), offer.getString("currency"), offer.getString("availability"), offer.getString("url"), offer.getLong("checkedAt"))
    }
    return found.distinctBy { it.second.url }
}

private suspend fun searchStore(store: Store, query: String, config: JSONObject, filterCategory: String?): Pair<List<Pair<String, Offer>>, String?> {
    val errors = mutableListOf<String>()
    var responded = false
    if (store.adapter == "neptun") {
        for (searchTerm in searchAlternatives(query, config)) {
            try {
                val raw = fetchNeptunSearch(store, searchTerm)
                responded = true
                val offers = parseNeptunResponse(store, raw, query, config, filterCategory)
                if (offers.isNotEmpty()) return offers to null
            } catch (e: Exception) { errors += (e.message ?: "Kërkimi dështoi") }
        }
        val useful = errors.distinct().filterNot { it == "HTTP 404" }
        val error = when { responded -> null; useful.isNotEmpty() -> useful.joinToString(", "); errors.isNotEmpty() -> "Nuk u gjet endpoint publik kërkimi"; else -> null }
        return emptyList<Pair<String, Offer>>() to error
    }
    val routes = when (store.adapter) {
        "cscart" -> listOf("cscart")
        "shopify" -> listOf("", "search", "store")
        "woocommerce" -> listOf("woo", "wpsearch", "", "search", "store")
        "shopware" -> listOf("search", "query", "store")
        else -> listOf("search", "shopsearch", "query", "products", "opencart", "store")
    }
    for (searchTerm in searchAlternatives(query, config)) {
        for (route in routes) {
            try {
                val raw = fetchText(queryUrl(store, searchTerm, route))
                responded = true
                val offers = parseSearchResponse(store, raw, query, config, filterCategory)
                if (offers.isNotEmpty()) return offers to null
                if (store.adapter == "woocommerce" && route == "woo" && raw.trimStart().startsWith("[") && runCatching { JSONArray(raw) }.isSuccess) break
                if (store.adapter == "shopify" && route.isEmpty()) {
                    val root = runCatching { JSONObject(raw) }.getOrNull()
                    val products = root?.optJSONObject("resources")?.optJSONObject("results")?.optJSONArray("products") ?: root?.optJSONArray("products")
                    if (products != null) break
                }
            } catch (e: Exception) { errors += (e.message ?: "Kërkimi dështoi") }
        }
    }
    val useful = errors.distinct().filterNot { it == "HTTP 404" }
    val error = when { responded -> null; useful.isNotEmpty() -> useful.joinToString(", "); errors.isNotEmpty() -> "Nuk u gjet endpoint publik kërkimi"; else -> null }
    return emptyList<Pair<String, Offer>>() to error
}
@Composable
private fun GjejmaScreen(context: android.content.Context, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Pair<String, Offer>>()) }
    var message by remember { mutableStateOf("Kërkimi kontrollon dyqanet drejtpërdrejt.") }
    var busy by remember { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf("") }
    val categoryOptions = remember { runCatching { readSearchConfig(context).optJSONObject("userCategories") ?: JSONObject() }.getOrDefault(JSONObject()) }
    val scope = rememberCoroutineScope()

    fun search(categoryFilter: String = selectedCategory) {
        val term = query.trim()
        if (term.length < 2 || busy) return
        busy = true; results = emptyList(); message = "Po kërkoj live në dyqanet shqiptare…"
        scope.launch {
            try {
                val directory = storeDirectory(context)
                val config = loadSearchConfig(context)
                val plan = selectStores(term, directory, config, categoryFilter)
                val answers = plan.stores.chunked(6).flatMap { batch ->
                    coroutineScope { batch.map { store -> async { store to searchStore(store, term, config, plan.filterCategory) } }.awaitAll() }
                }
                results = answers.flatMap { it.second.first }.sortedBy { it.second.price }
                val foundStores = answers.count { it.second.first.isNotEmpty() }
                val errors = answers.filter { it.second.second != null }.take(4).joinToString("\n") { "${it.first.name}: ${it.second.second}" }
                val category = plan.category?.let { " · ${categoryOptions.optJSONObject(it)?.optString("label", categoryLabels[it] ?: it) ?: categoryLabels[it] ?: it}" }.orEmpty()
                message = "Live · $foundStores/${plan.stores.size} dyqane me oferta · ${plan.stores.size}/${directory.size} të kontrolluara$category · ${results.size} oferta · ${java.text.SimpleDateFormat("HH:mm:ss", Locale("sq", "AL")).format(java.util.Date())}" + if (results.isEmpty() && errors.isNotBlank()) "\n$errors" else ""
            } catch (e: Exception) { message = "Kërkimi live dështoi: ${e.message ?: "kontrollo lidhjen"}" }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().background(Page).padding(horizontal = 18.dp, vertical = 18.dp)) {
        Text("gjejma.", color = Forest, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Çmime live nga 40+ dyqane shqiptare.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Kërko produkt") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), shape = RoundedCornerShape(12.dp))
        }
        Text("Provo: PS5 · S26 Ultra · TV · frigorifer · biçikletë elektrike", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
        Spacer(Modifier.height(10.dp))
        Text("Filtro sipas kategorisë (opsionale)", color = Muted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = selectedCategory.isBlank(), enabled = !busy, onClick = { selectedCategory=""; if(query.trim().length>=2) search("") }, label = { Text("Të gjitha") })
            val keys=categoryOptions.keys()
            while(keys.hasNext()) {
                val key=keys.next(); val label=categoryOptions.optJSONObject(key)?.optString("label",key) ?: key
                FilterChip(selected=selectedCategory==key,enabled=!busy,onClick={selectedCategory=key;if(query.trim().length>=2)search(key)},label={Text(label)})
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { search() }, enabled = !busy && query.trim().length >= 2, shape = RoundedCornerShape(12.dp)) {
                if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(8.dp)) }
                Text(if (busy) "Po kërkoj…" else "Kërko tani")
            }
            Spacer(Modifier.width(8.dp)); TextButton(onClick = { details = !details }) { Text(if (details) "Mbyll" else "Si funksionon", color = Forest) }
        }
        if (details) Text("Çdo kërkim dërgohet drejtpërdrejt te dyqanet. Ofertat nuk ruhen në telefon dhe kërkojnë internet. Disa faqe mund të mos përgjigjen ose të mos publikojnë listime të lexueshme; gabimet shfaqen te rezultati. Konfirmo çmimin dhe stokun te shitësi.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
        Text(message, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 5.dp))
        Spacer(Modifier.height(4.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results) { (title, offer) ->
                Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp, bottom = 1.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) { Text(offer.store, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Text(offer.domain, color = Muted, fontSize = 11.sp) }
                            Text(formatPrice(offer.price, offer.currency), color = Forest, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(6.dp)); Text(availabilityLabel(offer.availability), color = Muted, fontSize = 11.sp)
                        TextButton(onClick = { onOpen(offer.url) }, modifier = Modifier.align(Alignment.End)) { Text("Hap dyqanin ↗", color = Forest, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

private fun formatPrice(amount: Double, currency: String): String {
    val f = NumberFormat.getNumberInstance(Locale("sq", "AL")); f.maximumFractionDigits = 2
    return "${f.format(amount)} ${if (currency == "ALL") "L" else currency}"
}
private fun availabilityLabel(state: String) = when (state) {
    "in_stock" -> "Në stok sipas listimit · konfirmo te shitësi"
    "out_of_stock" -> "Jashtë stokut sipas listimit"
    "preorder" -> "Paraporosi sipas listimit"
    else -> "Stoku i panjohur · kontrollo te shitësi"
}





