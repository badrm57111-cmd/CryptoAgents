package com.example.agents

val STABLES = setOf("USDT", "USDC", "FDUSD", "TUSD", "USDP", "DAI", "AEUR", "EUR", "USD1", "XUSD")
val DOLLAR_QUOTES = listOf("USDT", "USDC", "FDUSD")           // أسواق التداول التجريبي (قيمتها بالدولار)
val ALL_QUOTES = listOf("USDT", "USDC", "FDUSD", "BTC", "ETH", "BNB")  // أسواق التحليل

data class MarketOverview(
    val quote: String,
    val pairs: Int,
    val advancers: Int,
    val decliners: Int,
    val avgChange: Double,
    val totalVolume: Double,
    val topGainers: List<Ticker>,
    val topLosers: List<Ticker>,
    val topVolume: List<Ticker>,
    val btc: Ticker?,
    val eth: Ticker?
) {
    val breadth: Double get() = if (pairs == 0) 0.0 else advancers * 100.0 / pairs
    val mood: String
        get() = when {
            pairs == 0 -> "لا توجد بيانات"
            breadth >= 60 -> "صاعد (أغلب العملات خضراء)"
            breadth <= 40 -> "هابط (أغلب العملات حمراء)"
            else -> "متذبذب"
        }
}

data class MarketRow(val quote: String, val pairs: Int, val breadthPct: Double, val avgChange: Double)

object MarketAnalysis {
    // حد السيولة بيتطبق على الأسواق الدولارية فقط، لأن حجم سوق BTC مثلاً بيتقاس بالـ BTC
    private fun usable(t: Ticker, minVol: Double) =
        t.base !in STABLES && t.quoteVolume > 0 &&
            (t.quote !in DOLLAR_QUOTES || t.quoteVolume >= minVol)

    fun overview(all: List<Ticker>, quote: String, minVol: Double): MarketOverview {
        val m = all.filter { it.quote == quote && usable(it, minVol) }
        return MarketOverview(
            quote = quote,
            pairs = m.size,
            advancers = m.count { it.changePct > 0 },
            decliners = m.count { it.changePct < 0 },
            avgChange = if (m.isEmpty()) 0.0 else m.map { it.changePct }.average(),
            totalVolume = m.sumOf { it.quoteVolume },
            topGainers = m.sortedByDescending { it.changePct }.take(5),
            topLosers = m.sortedBy { it.changePct }.take(5),
            topVolume = m.sortedByDescending { it.quoteVolume }.take(5),
            btc = all.firstOrNull { it.symbol == "BTCUSDT" },
            eth = all.firstOrNull { it.symbol == "ETHUSDT" }
        )
    }

    fun rows(all: List<Ticker>, minVol: Double): List<MarketRow> = ALL_QUOTES.map { q ->
        val m = all.filter { it.quote == q && usable(it, minVol) }
        MarketRow(
            q, m.size,
            if (m.isEmpty()) 0.0 else m.count { it.changePct > 0 } * 100.0 / m.size,
            if (m.isEmpty()) 0.0 else m.map { it.changePct }.average()
        )
    }
}
