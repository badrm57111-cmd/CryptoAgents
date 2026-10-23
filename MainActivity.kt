package com.example.agents

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: AgentsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Screen(vm) } }
    }
}

private fun f2(x: Double) = String.format(Locale.US, "%.2f", x)
private fun pct(x: Double) = String.format(Locale.US, "%+.2f%%", x)
private fun px(x: Double) = String.format(Locale.US, if (x >= 1) "%.4f" else "%.8f", x)
private fun vol(x: Double) = when {
    x >= 1e9 -> String.format(Locale.US, "%.2fB", x / 1e9)
    x >= 1e6 -> String.format(Locale.US, "%.1fM", x / 1e6)
    x >= 1e3 -> String.format(Locale.US, "%.1fK", x / 1e3)
    else -> String.format(Locale.US, "%.2f", x)
}

private val VOL_OPTIONS = listOf("1M" to 1e6, "10M" to 1e7, "50M" to 5e7)

@Composable
fun Chips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            if (o == selected) Button(onClick = { onSelect(o) }) { Text(o) }
            else OutlinedButton(onClick = { onSelect(o) }) { Text(o) }
        }
    }
}

@Composable
fun Screen(vm: AgentsViewModel) {
    val s by vm.ui.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    Surface(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.statusBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Text("Badr التحليل المالي", style = MaterialTheme.typography.titleLarge) }
            item {
                Text(
                    "الأسعار حقيقية من Binance، لكن الرصيد وهمي ولا يتم إرسال أي أمر حقيقي.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                Chips(listOf("الوكلاء", "تحليل السوق"), if (tab == 0) "الوكلاء" else "تحليل السوق") {
                    tab = if (it == "الوكلاء") 0 else 1
                    if (tab == 1) vm.ensureMarket()
                }
            }
            if (tab == 0) agentsTab(vm, s) else analysisTab(vm, s)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.agentsTab(vm: AgentsViewModel, s: UiState) {
    item {
        Text(
            "القيمة: ${f2(s.equity)} USDT   |   نقد: ${f2(s.cash)}",
            style = MaterialTheme.typography.titleMedium
        )
    }
    item {
        Row {
            Button(onClick = { vm.toggle() }) { Text(if (s.running) "إيقاف" else "تشغيل") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { vm.reset() }) { Text("إعادة ضبط") }
        }
    }
    item { Text("السوق المستهدف للتداول", style = MaterialTheme.typography.titleMedium) }
    item { Chips(DOLLAR_QUOTES, s.tradeQuote) { vm.setTradeQuote(it) } }
    item { Text("أقل سيولة 24 ساعة", style = MaterialTheme.typography.titleMedium) }
    item {
        val label = VOL_OPTIONS.firstOrNull { it.second == s.minVol }?.first ?: ""
        Chips(VOL_OPTIONS.map { it.first }, label) { l ->
            VOL_OPTIONS.firstOrNull { it.first == l }?.let { vm.setMinVol(it.second) }
        }
    }
    item { Text("المراكز المفتوحة", style = MaterialTheme.typography.titleMedium) }
    items(s.positions) { p ->
        Text("${p.symbol}  |  دخول ${px(p.entry)}  |  كمية ${px(p.qty)}")
    }
    item { Text("آخر الصفقات", style = MaterialTheme.typography.titleMedium) }
    items(s.trades) { t ->
        val pnl = t.pnl?.let { "  |  ${f2(it)}$" } ?: ""
        Text("${t.side} ${t.symbol} @ ${px(t.price)}$pnl", style = MaterialTheme.typography.bodySmall)
    }
    item { Text("سجل الوكلاء", style = MaterialTheme.typography.titleMedium) }
    items(s.logs) { Text(it, style = MaterialTheme.typography.bodySmall) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.analysisTab(vm: AgentsViewModel, s: UiState) {
    item { Text("اختر السوق", style = MaterialTheme.typography.titleMedium) }
    item { Chips(ALL_QUOTES, s.viewQuote) { vm.setViewQuote(it) } }
    item { OutlinedButton(onClick = { vm.refreshMarket() }) { Text("تحديث البيانات") } }
    if (s.loading) item { Text("جاري التحميل...") }
    s.error?.let { e -> item { Text("خطأ: $e", color = MaterialTheme.colorScheme.error) } }

    val o = s.overview
    if (o != null) {
        item { Text("نظرة عامة — سوق ${o.quote}", style = MaterialTheme.typography.titleMedium) }
        item {
            Text(
                "الأزواج: ${o.pairs}  |  صاعد: ${o.advancers}  هابط: ${o.decliners}  (${f2(o.breadth)}% صاعد)\n" +
                    "متوسط التغير: ${pct(o.avgChange)}  |  إجمالي الحجم: ${vol(o.totalVolume)} ${o.quote}\n" +
                    "المزاج العام: ${o.mood}"
            )
        }
        item {
            val btc = o.btc?.let { "BTC ${pct(it.changePct)}" } ?: ""
            val eth = o.eth?.let { "ETH ${pct(it.changePct)}" } ?: ""
            Text("المرجعيات (24س): $btc   $eth", style = MaterialTheme.typography.bodySmall)
        }
        item { Text("اضغط على أي عملة لتحليلها", style = MaterialTheme.typography.bodySmall) }
        item { Text("الأعلى ارتفاعاً", style = MaterialTheme.typography.titleSmall) }
        items(o.topGainers) { TickerRow(vm, it) }
        item { Text("الأكثر انخفاضاً", style = MaterialTheme.typography.titleSmall) }
        items(o.topLosers) { TickerRow(vm, it) }
        item { Text("الأعلى سيولة", style = MaterialTheme.typography.titleSmall) }
        items(o.topVolume) { TickerRow(vm, it) }
    }

    val d = s.detail
    if (s.detailSymbol != null && d != null) {
        item { Text("تحليل ${s.detailSymbol} (شموع 15 دقيقة)", style = MaterialTheme.typography.titleMedium) }
        item {
            Text(
                "RSI: ${String.format(Locale.US, "%.0f", d.rsi)}  |  قفزة الحجم: x${String.format(Locale.US, "%.1f", d.volSpike)}  |  النقاط: ${d.score}/3\n" +
                    (if (d.reason.isNotEmpty()) "${d.reason}\n" else "") +
                    (if (d.score >= 3) "الخلاصة: إشارة شراء حسب قواعد الوكلاء" else "الخلاصة: لا توجد إشارة")
            )
        }
    }

    item { Text("مقارنة الأسواق", style = MaterialTheme.typography.titleMedium) }
    items(s.markets) { m ->
        Text(
            "${m.quote}: ${m.pairs} زوج  |  ${f2(m.breadthPct)}% صاعد  |  متوسط ${pct(m.avgChange)}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun TickerRow(vm: AgentsViewModel, t: Ticker) {
    Text(
        "${t.base}   ${px(t.price)}   ${pct(t.changePct)}   حجم ${vol(t.quoteVolume)}",
        Modifier.fillMaxWidth().clickable { vm.analyze(t.symbol) }.padding(vertical = 6.dp)
    )
}
