package com.example.agents

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * بيانات السوق العامة من Binance، بدون مفتاح API وبدون صلاحية تداول.
 * data-api.binance.vision نطاق رسمي للبيانات العامة فقط.
 * لازم تُستدعى من thread خلفي (Dispatchers.IO).
 */
object BinanceApi {
    private const val BASE = "https://data-api.binance.vision/api/v3"

    private fun get(path: String): String {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        try {
            if (c.responseCode != 200) error("Binance HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    private val QUOTES = listOf("FDUSD", "USDT", "USDC", "BTC", "ETH", "BNB")

    /** كل أزواج التداول على Binance مع عملة التسعير (USDT, USDC, FDUSD, BTC, ETH, BNB). */
    fun tickers24h(): List<Ticker> {
        val arr = JSONArray(get("/ticker/24hr"))
        val out = ArrayList<Ticker>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val sym = o.getString("symbol")
            val q = QUOTES.firstOrNull { sym.endsWith(it) && sym.length > it.length } ?: continue
            val price = o.getString("lastPrice").toDouble()
            if (price <= 0.0) continue
            out.add(
                Ticker(
                    sym, sym.removeSuffix(q), q, price,
                    o.getString("priceChangePercent").toDouble(),
                    o.getString("quoteVolume").toDouble()
                )
            )
        }
        return out
    }

    /** يرجع (أسعار الإغلاق، أحجام التداول) لشموع 15 دقيقة. */
    fun klines(symbol: String, interval: String = "15m", limit: Int = 60): Pair<List<Double>, List<Double>> {
        val arr = JSONArray(get("/klines?symbol=$symbol&interval=$interval&limit=$limit"))
        val closes = ArrayList<Double>()
        val vols = ArrayList<Double>()
        for (i in 0 until arr.length()) {
            val k = arr.getJSONArray(i)
            closes.add(k.getString(4).toDouble())
            vols.add(k.getString(5).toDouble())
        }
        return closes to vols
    }
}
