package com.example.agents

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UiState(
    // الوكلاء
    val running: Boolean = false,
    val equity: Double = PaperBroker.START_CASH,
    val cash: Double = PaperBroker.START_CASH,
    val positions: List<Position> = emptyList(),
    val trades: List<Trade> = emptyList(),
    val logs: List<String> = emptyList(),
    // الإعدادات
    val tradeQuote: String = "USDT",
    val minVol: Double = 10_000_000.0,
    // تحليل السوق
    val viewQuote: String = "USDT",
    val overview: MarketOverview? = null,
    val markets: List<MarketRow> = emptyList(),
    val detailSymbol: String? = null,
    val detail: Signal? = null,
    val loading: Boolean = false,
    val error: String? = null
)

class AgentsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("agents", Context.MODE_PRIVATE)
    private var broker: PaperBroker =
        prefs.getString("broker", null)?.let { runCatching { PaperBroker.fromJson(it) }.getOrNull() }
            ?: PaperBroker()

    private var tradeQuote = prefs.getString("tradeQuote", "USDT") ?: "USDT"
    private var minVol = prefs.getLong("minVol", 10_000_000L).toDouble()
    private var viewQuote = tradeQuote

    private var lastAll: List<Ticker> = emptyList()
    private var lastPrices: Map<String, Double> = emptyMap()
    private var overview: MarketOverview? = null
    private var markets: List<MarketRow> = emptyList()
    private var detailSymbol: String? = null
    private var detail: Signal? = null
    private var loading = false
    private var error: String? = null

    private val logs = ArrayDeque<String>()
    private var job: Job? = null

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    init { publish() }

    // ---------- الوكلاء ----------
    fun toggle() {
        if (job?.isActive == true) {
            job?.cancel()
            job = null
        } else {
            job = viewModelScope.launch(Dispatchers.IO) {
                while (isActive) {
                    cycle()
                    delay(60_000)
                }
            }
        }
        publish()
    }

    fun reset() {
        viewModelScope.launch(Dispatchers.IO) {
            job?.cancelAndJoin()
            job = null
            broker = PaperBroker()
            logs.clear()
            save()
            publish()
        }
    }

    private fun cycle() {
        try {
            val all = BinanceApi.tickers24h()
            applyMarket(all)

            // القناص: خروج أولاً (وقف خسارة / جني ربح)
            broker.checkExits(lastPrices).forEach {
                log("القناص: بيع ${it.symbol} @ ${fmt(it.price)} | النتيجة ${fmt(it.pnl ?: 0.0)}$")
            }

            // المحقق: في السوق المختار فقط
            val cands = Scout.pick(all, tradeQuote, minVol)
            log("المحقق [$tradeQuote]: ${cands.size} مرشحين " + cands.take(3).joinToString { it.base })

            // البروفيسور ثم القناص
            for (t in cands) {
                val s = Analyst.analyze(t) ?: continue
                if (s.score >= 3) {
                    log("البروفيسور: ${t.symbol} إشارة شراء (${s.score}/3) — ${s.reason}")
                    broker.buy(s, lastPrices)?.let {
                        log("القناص: شراء ${it.symbol} @ ${fmt(it.price)}")
                    }
                }
            }
            save()
            error = null
        } catch (e: Exception) {
            log("خطأ: ${e.message}")
        }
        publish()
    }

    // ---------- الإعدادات ----------
    fun setTradeQuote(q: String) {
        tradeQuote = q
        prefs.edit().putString("tradeQuote", q).apply()
        publish()
    }

    fun setMinVol(v: Double) {
        minVol = v
        prefs.edit().putLong("minVol", v.toLong()).apply()
        if (lastAll.isNotEmpty()) recompute()
        publish()
    }

    // ---------- تحليل السوق ----------
    fun ensureMarket() {
        if (lastAll.isEmpty()) refreshMarket()
    }

    fun setViewQuote(q: String) {
        viewQuote = q
        detail = null
        detailSymbol = null
        if (lastAll.isNotEmpty()) { recompute(); publish() } else refreshMarket()
    }

    fun refreshMarket() {
        viewModelScope.launch(Dispatchers.IO) {
            loading = true
            publish()
            try {
                applyMarket(BinanceApi.tickers24h())
                error = null
            } catch (e: Exception) {
                error = e.message
            }
            loading = false
            publish()
        }
    }

    fun analyze(symbol: String) {
        val t = lastAll.firstOrNull { it.symbol == symbol } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            loading = true
            detailSymbol = symbol
            detail = null
            publish()
            try {
                detail = Analyst.analyze(t)
                error = if (detail == null) "بيانات الشموع غير كافية لهذا الزوج" else null
            } catch (e: Exception) {
                error = e.message
            }
            loading = false
            publish()
        }
    }

    // ---------- داخلي ----------
    private fun applyMarket(all: List<Ticker>) {
        lastAll = all
        lastPrices = all.associate { it.symbol to it.price }
        recompute()
    }

    private fun recompute() {
        overview = MarketAnalysis.overview(lastAll, viewQuote, minVol)
        markets = MarketAnalysis.rows(lastAll, minVol)
    }

    private fun fmt(x: Double) = "%.4f".format(Locale.US, x)

    private fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logs.addFirst("$time  $msg")
        while (logs.size > 60) logs.removeLast()
    }

    private fun save() = prefs.edit().putString("broker", broker.toJson()).apply()

    private fun publish() {
        _ui.value = UiState(
            running = job?.isActive == true,
            equity = broker.equity(lastPrices),
            cash = broker.cash,
            positions = broker.positions.toList(),
            trades = broker.trades.take(30),
            logs = logs.toList(),
            tradeQuote = tradeQuote,
            minVol = minVol,
            viewQuote = viewQuote,
            overview = overview,
            markets = markets,
            detailSymbol = detailSymbol,
            detail = detail,
            loading = loading,
            error = error
        )
    }
}
