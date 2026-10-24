package al.gjejma.app

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
                    GjejmaScreen { link ->
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

private fun normalize(s: String) = java.text.Normalizer.normalize(s.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

private fun queryUrl(store: Store, query: String, route: String = ""): String {
    val encoded = java.net.URLEncoder.encode(query, "UTF-8")
    return when {
        store.adapter == "shopify" && route.isEmpty() -> "https://${store.domain}/search/suggest.json?q=$encoded&resources[type]=product&resources[limit]=20"
        route == "woo" -> "https://${store.domain}/wp-json/wc/store/v1/products?search=$encoded&per_page=30"
        route == "search" -> "https://${store.domain}/search?q=$encoded"
        route == "store" -> "${store.url}${if (store.url.contains('?')) '&' else '?'}s=$encoded&post_type=product"
        else -> "https://${store.domain}/?s=$encoded&post_type=product"
    }
}

private fun parseSearchResponse(store: Store, raw: String, query: String): List<Pair<String, Offer>> {
    val found = mutableListOf<Pair<String, Offer>>()
    val terms = normalize(query).split(" ").filter(String::isNotBlank)
    fun add(title: String, link: String, price: Double?, currency: String, stock: String, identity: String) {
        if (price == null || title.isBlank() || !terms.all(normalize(title)::contains)) return
        val offer = safeProduct(store, title, link, price, currency, stock, identity) ?: return
        val o = offer.getJSONObject("offer")
        found += title to Offer(store.name, store.domain, o.getDouble("price"), o.getString("currency"), o.getString("availability"), o.getString("url"), o.getLong("checkedAt"))
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

private suspend fun searchStore(store: Store, query: String): Pair<List<Pair<String, Offer>>, String?> {
    val routes = if (store.adapter == "shopify") listOf("", "search", "store") else listOf("woo", "", "store", "search")
    val errors = mutableListOf<String>()
    for (route in routes) {
        try {
            val raw = fetchText(queryUrl(store, query, route))
            val offers = parseSearchResponse(store, raw, query)
            if (offers.isNotEmpty()) return offers to null
        } catch (e: Exception) { errors += (e.message ?: "Kërkimi dështoi") }
    }
    return emptyList<Pair<String, Offer>>() to (errors.distinct().joinToString(", ").ifBlank { "Nuk u gjet listim publik që përputhet" })
}
@Composable
private fun GjejmaScreen(onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Pair<String, Offer>>()) }
    var message by remember { mutableStateOf("Kërkimi kontrollon dyqanet drejtpërdrejt.") }
    var busy by remember { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun search() {
        val term = query.trim()
        if (term.length < 2 || busy) return
        busy = true; results = emptyList(); message = "Po kërkoj live në dyqanet shqiptare…"
        scope.launch {
            try {
                val stores = storeDirectory()
                val answers = coroutineScope { stores.map { store -> async { store to searchStore(store, term) } }.awaitAll() }
                results = answers.flatMap { it.second.first }.sortedBy { it.second.price }
                val foundStores = answers.count { it.second.first.isNotEmpty() }
                val errors = answers.filter { it.second.second != null }.take(4).joinToString("\n") { "${it.first.name}: ${it.second.second}" }
                message = "Live · $foundStores/${stores.size} dyqane me oferta · ${results.size} oferta · kontrolluar ${java.text.SimpleDateFormat("HH:mm:ss", Locale("sq", "AL")).format(java.util.Date())}" + if (results.isEmpty() && errors.isNotBlank()) "\n$errors" else ""
            } catch (e: Exception) { message = "Kërkimi live dështoi: ${e.message ?: "kontrollo lidhjen"}" }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().background(Page).padding(horizontal = 18.dp, vertical = 18.dp)) {
        Text("gjejma.", color = Forest, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Çmime live nga dyqanet shqiptare.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), label = { Text("Kërko produkt") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.width(8.dp)); Button(onClick = { search() }, enabled = !busy, shape = RoundedCornerShape(12.dp)) { Text("Gjej") }
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





