package com.example.agents

import org.json.JSONArray
import org.json.JSONObject

data class Ticker(val symbol: String, val base: String, val quote: String, val price: Double, val changePct: Double, val quoteVolume: Double)
data class Signal(val symbol: String, val price: Double, val score: Int, val rsi: Double, val volSpike: Double, val reason: String)
data class Position(val symbol: String, val qty: Double, val entry: Double)
data class Trade(val time: Long, val symbol: String, val side: String, val price: Double, val qty: Double, val pnl: Double?)

/** وسيط تجريبي: رصيد وهمي بأسعار حقيقية. لا يرسل أي أمر حقيقي لأي منصة. */
class PaperBroker(
    var cash: Double = START_CASH,
    val positions: MutableList<Position> = mutableListOf(),
    val trades: MutableList<Trade> = mutableListOf()
) {
    companion object {
        const val START_CASH = 1000.0
        const val MAX_POSITIONS = 3
        const val SIZE_PCT = 0.10      // 10% من إجمالي القيمة لكل صفقة
        const val STOP_LOSS = -0.03    // وقف خسارة 3%
        const val TAKE_PROFIT = 0.06   // جني ربح 6%
        const val FEE = 0.001          // عمولة 0.1% لكل جهة

        fun fromJson(s: String): PaperBroker {
            val o = JSONObject(s)
            val b = PaperBroker(o.getDouble("cash"))
            val ps = o.getJSONArray("positions")
            for (i in 0 until ps.length()) {
                val j = ps.getJSONObject(i)
                b.positions.add(Position(j.getString("s"), j.getDouble("q"), j.getDouble("e")))
            }
            val ts = o.getJSONArray("trades")
            for (i in 0 until ts.length()) {
                val j = ts.getJSONObject(i)
                b.trades.add(
                    Trade(
                        j.getLong("t"), j.getString("s"), j.getString("d"),
                        j.getDouble("p"), j.getDouble("q"),
                        if (j.isNull("l")) null else j.getDouble("l")
                    )
                )
            }
            return b
        }
    }

    fun equity(prices: Map<String, Double>): Double =
        cash + positions.sumOf { it.qty * (prices[it.symbol] ?: it.entry) }

    fun buy(s: Signal, prices: Map<String, Double>): Trade? {
        if (positions.size >= MAX_POSITIONS || positions.any { it.symbol == s.symbol }) return null
        val budget = equity(prices) * SIZE_PCT
        if (budget > cash || budget < 10.0) return null
        val qty = budget * (1 - FEE) / s.price
        cash -= budget
        positions.add(Position(s.symbol, qty, s.price))
        val t = Trade(System.currentTimeMillis(), s.symbol, "BUY", s.price, qty, null)
        trades.add(0, t)
        return t
    }

    fun checkExits(prices: Map<String, Double>): List<Trade> {
        val out = mutableListOf<Trade>()
        val iter = positions.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            val px = prices[p.symbol] ?: continue
            val chg = px / p.entry - 1
            if (chg <= STOP_LOSS || chg >= TAKE_PROFIT) {
                val proceeds = p.qty * px * (1 - FEE)
                val cost = p.qty * p.entry / (1 - FEE)
                cash += proceeds
                iter.remove()
                val t = Trade(System.currentTimeMillis(), p.symbol, "SELL", px, p.qty, proceeds - cost)
                trades.add(0, t)
                out.add(t)
            }
        }
        return out
    }

    fun toJson(): String = JSONObject().apply {
        put("cash", cash)
        put("positions", JSONArray().also { a ->
            positions.forEach { a.put(JSONObject().put("s", it.symbol).put("q", it.qty).put("e", it.entry)) }
        })
        put("trades", JSONArray().also { a ->
            trades.take(100).forEach {
                a.put(
                    JSONObject().put("t", it.time).put("s", it.symbol).put("d", it.side)
                        .put("p", it.price).put("q", it.qty).put("l", it.pnl ?: JSONObject.NULL)
                )
            }
        })
    }.toString()
}
