package al.gjejma.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.delay
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
        val catalog = LocalCatalog(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Page) {
                    GjejmaScreen(catalog, applicationContext) { link ->
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                    }
                }
            }
        }
    }
}

private const val STORE_DIRECTORY = """[
 {"id":"3vfejzo","name":"3V Fejzo","domain":"3vfejzo.al","url":"https://3vfejzo.al/products/telefona","enabled":true,"adapter":"woocommerce","country":"AL","currency":"ALL"},
 {"id":"red-store","name":"Red Store","domain":"redstore.al","url":"https://redstore.al/","enabled":true,"adapter":"woocommerce","country":"AL","currency":"ALL"},
 {"id":"smartphoneshop","name":"The Smartphone Shop","domain":"smartphoneshop.al","url":"https://smartphoneshop.al/","enabled":true,"adapter":"jsonld","country":"AL","currency":"ALL"},
 {"id":"bss","name":"BSS Buy Smart Store","domain":"bss.com.al","url":"https://bss.com.al/","enabled":true,"adapter":"woocommerce","country":"AL","currency":"ALL"},
 {"id":"gotech","name":"GoTech Electronics","domain":"gotech.al","url":"https://gotech.al/product-category/telefonia/","enabled":true,"adapter":"woocommerce","country":"AL","currency":"ALL"},
 {"id":"phoneshopalbania","name":"Phone Shop Albania","domain":"phoneshopalbania.al","url":"https://phoneshopalbania.al/","enabled":true,"adapter":"woocommerce","country":"AL","currency":"ALL"},
 {"id":"redline","name":"Redline","domain":"redline.com.al","url":"https://redline.com.al/","enabled":true,"adapter":"shopify","country":"AL","currency":"ALL"},
 {"id":"smartstore","name":"Smart Store","domain":"smartstore.al","url":"https://smartstore.al/","enabled":true,"adapter":"woocommerce","country":"AL","currency":"EUR"},
 {"id":"agorai","name":"Agorai","domain":"agorai.al","url":"https://www.agorai.al/","enabled":true,"adapter":"woocommerce","country":"AL","currency":"ALL"},
 {"id":"shoptech","name":"ShopTech","domain":"shoptech.al","url":"https://shoptech.al/","enabled":true,"adapter":"woocommerce","country":"AL","currency":"ALL"},
 {"id":"boostmobile","name":"Boost Mobile","domain":"boostmobile.al","url":"https://boostmobile.al/en/","enabled":true,"adapter":"woocommerce","country":"AL","currency":"ALL"}
]"""
private data class Store(val id: String, val name: String, val domain: String, val url: String, val adapter: String, val currency: String)
private data class Offer(val store: String, val domain: String, val price: Double, val currency: String, val availability: String, val url: String, val checkedAt: Long)
private data class SearchResult(val title: String, val offers: List<Offer>)

private class LocalCatalog(context: Context) {
    private val prefs = context.getSharedPreferences("gjejma-local", Context.MODE_PRIVATE)
    fun stores(): List<Store> {
        val array = JSONArray(prefs.getString("stores", "[]"))
        return buildList {
            for (i in 0 until array.length()) {
                val s = array.getJSONObject(i)
                add(Store(s.getString("id"), s.getString("name"), s.getString("domain"), s.getString("url"), s.optString("adapter", "jsonld"), s.optString("currency", "ALL")))
            }
        }
    }
    fun saveStores(list: List<Store>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("domain", it.domain).put("url", it.url).put("adapter", it.adapter).put("currency", it.currency)) }
        prefs.edit().putString("stores", a.toString()).apply()
    }
    private fun products() = JSONArray(prefs.getString("products", "[]"))
    fun count() = products().length()
    fun putProducts(rows: List<JSONObject>) {
        val all = products()
        val indexes = HashMap<String, Int>()
        for (i in 0 until all.length()) indexes[all.getJSONObject(i).getString("key")] = i
        rows.forEach { row ->
            val old = indexes[row.getString("key")]
            if (old == null) { indexes[row.getString("key")] = all.length(); all.put(row) } else all.put(old, row)
        }
        prefs.edit().putString("products", all.toString()).apply()
    }
    fun search(query: String): List<SearchResult> {
        val terms = normalize(query).split(" ").filter { it.length > 1 }
        if (terms.isEmpty()) return emptyList()
        val all = products()
        val matches = mutableListOf<SearchResult>()
        for (i in 0 until all.length()) {
            val row = all.getJSONObject(i)
            val title = row.optString("title")
            val text = normalize(title)
            if (!terms.all(text::contains)) continue
            val o = row.getJSONObject("offer")
            matches += SearchResult(title, listOf(Offer(o.optString("store"), o.optString("domain"), o.optDouble("price"), o.optString("currency", "ALL"), o.optString("availability", "unknown"), o.optString("url"), o.optLong("checkedAt"))))
        }
        return matches.groupBy { it.title }.map { (title, rows) -> SearchResult(title, rows.flatMap { it.offers }.sortedBy { it.price }) }.sortedBy { it.offers.minOfOrNull(Offer::price) ?: Double.MAX_VALUE }
    }
}

private suspend fun fetchText(address: String): String = withContext(Dispatchers.IO) {
    val c = URL(address).openConnection() as HttpURLConnection
    c.connectTimeout = 10_000; c.readTimeout = 16_000; c.instanceFollowRedirects = true
    c.setRequestProperty("User-Agent", "GjejmaAndroid/1.0 (+https://github.com/FrenkliK/Gjejma; public product catalog)")
    c.setRequestProperty("Accept", "application/json,text/html;q=0.9,*/*;q=0.8")
    try { if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}"); c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
}

private suspend fun storeDirectory(): List<Store> = withContext(Dispatchers.IO) {
    val a = JSONArray(STORE_DIRECTORY)
    buildList {
        for (i in 0 until a.length()) {
            val s = a.getJSONObject(i)
            if (s.optBoolean("enabled") && s.optString("country") == "AL") add(Store(s.getString("id"), s.getString("name"), s.getString("domain"), s.getString("url"), s.optString("adapter", "jsonld"), s.optString("currency", "ALL")))
        }
    }
}

private fun robotsAllows(raw: String, path: String): Boolean {
    var relevant = false; var hasRules = false
    val rules = mutableListOf<Pair<Boolean, String>>()
    raw.lineSequence().forEach { source ->
        val line = source.substringBefore('#').trim(); val colon = line.indexOf(':')
        if (colon <= 0) return@forEach
        val key = line.substring(0, colon).trim().lowercase(); val value = line.substring(colon + 1).trim()
        if (key == "user-agent") {
            if (hasRules) { relevant = false; hasRules = false }
            if (value == "*" || value.lowercase().contains("gjejma")) relevant = true
        } else if (relevant && (key == "allow" || key == "disallow")) {
            hasRules = true
            if (value.isNotEmpty()) rules += (key == "allow") to value
        }
    }
    val winner = rules.filter { path.startsWith(it.second.substringBefore('*')) }.maxByOrNull { it.second.length } ?: return true
    return winner.first
}

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
    val uri = runCatching { Uri.parse(link) }.getOrNull() ?: return null
    val host = uri.host ?: return null
    if (host != store.domain && !host.endsWith(".${store.domain}")) return null
    val availability = when {
        stock.contains("out", true) || stock.contains("sold", true) -> "out_of_stock"
        stock.contains("instock", true) || stock.contains("in stock", true) || stock.contains("available", true) -> "in_stock"
        else -> "unknown"
    }
    val offer = JSONObject().put("store", store.name).put("domain", store.domain).put("price", price).put("currency", currency).put("availability", availability).put("url", link).put("checkedAt", System.currentTimeMillis())
    return JSONObject().put("key", "${store.id}:$identity").put("title", title).put("offer", offer)
}

private suspend fun collectWoo(store: Store): List<JSONObject> {
    val robots = runCatching { fetchText("https://${store.domain}/robots.txt") }.getOrElse { if (it.message?.contains("HTTP 404") == true) "" else throw it }
    val route = "/wp-json/wc/store/v1/products"
    if (!robotsAllows(robots, route)) error("robots.txt blocks product catalogue")
    val output = mutableListOf<JSONObject>()
    for (page in 1..5) {
        val array = JSONArray(fetchText("https://${store.domain}$route?per_page=100&page=$page"))
        for (i in 0 until array.length()) {
            val p = array.getJSONObject(i); val prices = p.optJSONObject("prices")
            val raw = prices?.optString("price")?.takeIf(String::isNotBlank) ?: p.optString("price")
            var amount = parsePrice(raw) ?: continue
            val minor = prices?.optInt("currency_minor_unit", 0) ?: 0
            if (minor > 0 && prices?.optString("price")?.isNotBlank() == true) amount /= Math.pow(10.0, minor.toDouble())
            safeProduct(store, p.optString("name"), p.optString("permalink"), amount, prices?.optString("currency_code")?.takeIf(String::isNotBlank) ?: store.currency, p.optString("stock_status"), p.optString("sku").ifBlank { p.optString("id") })?.let(output::add)
        }
        if (array.length() < 100) break
        delay(350)
    }
    return output
}

private suspend fun collectJsonLd(store: Store): List<JSONObject> {
    val robots = runCatching { fetchText("https://${store.domain}/robots.txt") }.getOrElse { if (it.message?.contains("HTTP 404") == true) "" else throw it }
    val path = Uri.parse(store.url).path ?: "/"
    if (!robotsAllows(robots, path)) error("robots.txt blocks catalogue page")
    val html = fetchText(store.url)
    val script = Regex("<script[^>]+type=[\"']application/ld\\+json[\"'][^>]*>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE)
    val output = mutableListOf<JSONObject>()
    for (match in script.findAll(html)) {
        val root = runCatching { JSONObject(match.groupValues[1]) }.getOrNull() ?: continue
        val graph = root.optJSONArray("@graph") ?: JSONArray().put(root)
        for (i in 0 until graph.length()) {
            val p = graph.optJSONObject(i) ?: continue
            if (p.optString("@type") != "Product") continue
            val offers = p.optJSONArray("offers")?.optJSONObject(0) ?: p.optJSONObject("offers") ?: continue
            val price = parsePrice(offers.opt("price") ?: offers.opt("lowPrice")) ?: continue
            val link = p.optString("url").ifBlank { offers.optString("url", store.url) }
            safeProduct(store, p.optString("name"), link, price, offers.optString("priceCurrency", store.currency), offers.optString("availability"), p.optString("sku").ifBlank { link })?.let(output::add)
        }
    }
    return output
}

private suspend fun collect(store: Store) = if (store.adapter == "woocommerce") collectWoo(store) else collectJsonLd(store)
private fun normalize(s: String) = java.text.Normalizer.normalize(s.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

@Composable
private fun GjejmaScreen(db: LocalCatalog, context: Context, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<SearchResult>()) }
    var message by remember { mutableStateOf("${db.count()} oferta të ruajtura në telefon.") }
    var busy by remember { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun search() {
        results = db.search(query)
        message = when { query.isBlank() -> "${db.count()} oferta të ruajtura në telefon."; results.isEmpty() -> "Nuk u gjet në katalogun lokal. Rifresko dyqanet me internet."; else -> "${results.sumOf { it.offers.size }} oferta · kontrollo çmimin te shitësi." }
    }
    fun refresh() {
        if (busy) return
        busy = true; message = "Po kontrolloj katalogët publikë. Kjo mund të zgjasë disa minuta…"
        scope.launch {
            try {
                val stores = if (db.stores().isEmpty()) storeDirectory().also(db::saveStores) else db.stores()
                var ok = 0; var count = 0; val errors = mutableListOf<String>()
                for (store in stores) {
                    try { val rows = collect(store); db.putProducts(rows); ok++; count += rows.size }
                    catch (e: Exception) { errors += "${store.name}: ${e.message ?: "s’u arrit"}" }
                }
                search()
                message = if (ok == 0) "Asnjë dyqan nuk dha feed publik tani. ${errors.firstOrNull().orEmpty()}" else "U ruajtën $count produkte nga $ok/${stores.size} dyqane. Kërkimi tani punon offline."
            } catch (_: Exception) { message = "Nuk u shkarkua lista e dyqaneve. Kontrollo internetin dhe provo sërish." }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().background(Page).padding(horizontal = 18.dp, vertical = 18.dp)) {
        Text("gjejma.", color = Forest, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Katalog në telefon. Kërko edhe offline.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it; results = db.search(it) }, Modifier.weight(1f), label = { Text("Kërko produkt") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.width(8.dp)); Button(onClick = { search() }, shape = RoundedCornerShape(12.dp)) { Text("Gjej") }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { refresh() }, enabled = !busy, shape = RoundedCornerShape(12.dp)) {
                if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(8.dp)) }
                Text(if (busy) "Po rifreskohet…" else "Rifresko dyqanet")
            }
            Spacer(Modifier.width(8.dp)); TextButton(onClick = { details = !details }) { Text(if (details) "Mbyll" else "Si funksionon", color = Forest) }
        }
        if (details) Text("Rifreskimi kërkon internet dhe lexon feed-e publike që lejojnë kërkesat. Çmimet ruhen në këtë telefon; kërkimi pas rifreskimit punon offline. Kontrollo çmimin dhe stokun te dyqani para blerjes.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
        Text(message, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 5.dp))
        Spacer(Modifier.height(4.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results) { product ->
                Text(product.title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp, bottom = 1.dp))
                product.offers.forEach { offer ->
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





