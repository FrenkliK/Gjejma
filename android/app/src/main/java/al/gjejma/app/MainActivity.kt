package al.gjejma.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val prefs = getSharedPreferences("gjejma", MODE_PRIVATE)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Page) {
                    GjejmaScreen(
                        initialBaseUrl = prefs.getString("baseUrl", "http://10.0.2.2:3000") ?: "http://10.0.2.2:3000",
                        onSaveBaseUrl = { prefs.edit().putString("baseUrl", it).apply() },
                        onOpenOffer = { link -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                    )
                }
            }
        }
    }
}

private data class Offer(val store: String, val domain: String, val price: Double, val currency: String,
                         val availability: String, val url: String, val checkedAt: String)

private data class SearchResult(val title: String, val offers: List<Offer>)

private suspend fun searchApi(baseUrl: String, query: String): List<SearchResult> = withContext(Dispatchers.IO) {
    val endpoint = baseUrl.trimEnd('/') + "/api/search?q=" + Uri.encode(query)
    val connection = URL(endpoint).openConnection() as HttpURLConnection
    connection.connectTimeout = 8_000
    connection.readTimeout = 12_000
    connection.requestMethod = "GET"
    try {
        if (connection.responseCode !in 200..299) error("Server returned ${connection.responseCode}")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(body)
        val productsJson = root.optJSONArray("products") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until productsJson.length()) {
                val product = productsJson.getJSONObject(i)
                val offerJson = product.optJSONArray("offers") ?: continue
                val offers = buildList {
                    for (j in 0 until offerJson.length()) {
                        val item = offerJson.getJSONObject(j)
                        add(Offer(
                            store = item.optString("store", "Dyqan"),
                            domain = item.optString("domain", ""),
                            price = item.optDouble("price", 0.0),
                            currency = item.optString("currency", "ALL"),
                            availability = item.optString("availability", "unknown"),
                            url = item.optString("url", ""),
                            checkedAt = item.optString("checkedAt", "")
                        ))
                    }
                }
                if (offers.isNotEmpty()) add(SearchResult(product.optString("title", "Produkt"), offers))
            }
        }
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun GjejmaScreen(initialBaseUrl: String, onSaveBaseUrl: (String) -> Unit, onOpenOffer: (String) -> Unit) {
    var baseUrl by rememberSaveable { mutableStateOf(initialBaseUrl) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var message by remember { mutableStateOf("Vendos adresën e serverit dhe kërko produktin.") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(Page).padding(horizontal = 20.dp, vertical = 20.dp)) {
        Text("gjejma.", color = Forest, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        Text("Gjej çmimin. Jo xhiron.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = baseUrl, onValueChange = { baseUrl = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Adresa e Gjejma server") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        Text("Emulatori: http://10.0.2.2:3000 · Telefoni: përdor IP-në e kompjuterit në Wi‑Fi.",
            color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f),
                label = { Text("Kërko produkt") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (query.trim().length < 2) { message = "Shkruaj të paktën 2 karaktere."; return@Button }
                onSaveBaseUrl(baseUrl)
                busy = true
                message = "Po kërkoj…"
                scope.launch {
                    try {
                        results = searchApi(baseUrl, query.trim())
                        message = if (results.isEmpty()) "Nuk u gjet ofertë e mbledhur për këtë produkt." else "${results.sumOf { it.offers.size }} oferta · kontrollo çmimin te dyqani."
                    } catch (e: Exception) {
                        message = "Nuk u lidhëm me serverin. Kontrollo adresën dhe rrjetin."
                    } finally { busy = false }
                }
            }, enabled = !busy, shape = RoundedCornerShape(12.dp)) { Text(if (busy) "…" else "Gjej") }
        }
        Spacer(Modifier.height(12.dp))
        Text(message, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results) { product ->
                Text(product.title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                product.offers.forEach { offer ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(offer.store, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(offer.domain, color = Muted, fontSize = 11.sp)
                                }
                                Text(formatPrice(offer.price, offer.currency), color = Forest, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(availabilityLabel(offer.availability), color = Muted, fontSize = 11.sp)
                            TextButton(onClick = { if (offer.url.isNotBlank()) onOpenOffer(offer.url) }, modifier = Modifier.align(Alignment.End)) {
                                Text("Hap dyqanin ↗", color = Forest, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatPrice(amount: Double, currency: String): String {
    val formatter = NumberFormat.getNumberInstance(Locale("sq", "AL"))
    formatter.maximumFractionDigits = 2
    return "${formatter.format(amount)} ${if (currency == "ALL") "L" else currency}"
}

private fun availabilityLabel(state: String) = when (state) {
    "in_stock" -> "Në stok sipas listimit · konfirmo te shitësi"
    "out_of_stock" -> "Jashtë stokut sipas listimit"
    "preorder" -> "Paraporosi sipas listimit"
    else -> "Stoku i panjohur · kontrollo te shitësi"
}
