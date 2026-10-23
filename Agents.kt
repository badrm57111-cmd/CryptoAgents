package com.example.agents

/** الوكيل 1 — المحقق: يفلتر السوق المختار ويطلع المرشحين. */
object Scout {
    fun pick(all: List<Ticker>, quote: String, minVol: Double, n: Int = 8): List<Ticker> =
        all.filter {
            it.quote == quote &&
                it.base !in STABLES &&
                it.quoteVolume >= minVol &&           // سيولة كافية
                it.changePct in 2.0..25.0             // صاعدة لكن مش منفجرة بالفعل
        }.sortedByDescending { it.changePct }.take(n)
}

/** الوكيل 2 — البروفيسور: تحليل فني بسيط (SMA20 + RSI + قفزة الحجم). */
object Analyst {
    fun analyze(t: Ticker): Signal? {
        val (closes, vols) = BinanceApi.klines(t.symbol)
        if (closes.size < 30) return null
        val c = closes.dropLast(1)   // نتجاهل الشمعة الحالية غير المكتملة
        val v = vols.dropLast(1)
        val sma20 = c.takeLast(20).average()
        val rsi = rsi(c)
        val avgVol = v.dropLast(1).takeLast(20).average()
        val spike = if (avgVol > 0) v.last() / avgVol else 0.0

        var score = 0
        val why = mutableListOf<String>()
        if (c.last() > sma20) { score++; why += "السعر فوق SMA20" }
        if (rsi in 50.0..68.0) { score++; why += "RSI=" + "%.0f".format(rsi) }
        if (spike >= 1.5) { score++; why += "الحجم x" + "%.1f".format(spike) }
        if (rsi > 75) score--
        return Signal(t.symbol, t.price, score, rsi, spike, why.joinToString("، "))
    }

    fun rsi(c: List<Double>, p: Int = 14): Double {
        var g = 0.0
        var l = 0.0
        for (i in 1..p) {
            val d = c[i] - c[i - 1]
            if (d >= 0) g += d else l -= d
        }
        var ag = g / p
        var al = l / p
        for (i in p + 1 until c.size) {
            val d = c[i] - c[i - 1]
            ag = (ag * (p - 1) + maxOf(d, 0.0)) / p
            al = (al * (p - 1) + maxOf(-d, 0.0)) / p
        }
        return if (al == 0.0) 100.0 else 100 - 100 / (1 + ag / al)
    }
}

// الوكيل 3 — القناص: هو PaperBroker (الشراء + وقف الخسارة + جني الربح).
