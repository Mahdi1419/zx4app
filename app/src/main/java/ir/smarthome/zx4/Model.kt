package ir.smarthome.zx4

import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/** delaySec = مدت انتظار بعد از ارسال این پیامک تا ارسال پیامک بعدی */
data class Step(val cmd: String, val delaySec: Int, val waitForReply: Boolean = false, val timeoutSec: Int = 60)

data class Schedule(
    val enabled: Boolean = false,
    val everyN: Boolean = false,        // false = روزهای هفته ، true = هر N روز
    val days: Set<Int> = emptySet(),    // Calendar.DAY_OF_WEEK (یکشنبه=1 ... شنبه=7)
    val n: Int = 2,
    val anchorDay: Long = 0,
    val hour: Int = 8,
    val minute: Int = 0,
)

data class Item(val id: String, val name: String, val icon: String, val color: Int,
                val steps: List<Step>, val schedule: Schedule = Schedule())

data class Reply(val at: Long, val text: String)
data class Entry(val id: String, val itemId: String?, val itemName: String, val cmd: String,
                 val at: Long, val source: String, val error: String?, val replies: List<Reply>)
data class Pending(val idx: Int, val at: Long, val waitingReply: Boolean = false, val source: String = "manual")
data class Template(val label: String, val cmd: String)

val Days = listOf(Triple(7, "ش", "شنبه"), Triple(1, "ی", "یکشنبه"), Triple(2, "د", "دوشنبه"),
    Triple(3, "س", "سه‌شنبه"), Triple(4, "چ", "چهارشنبه"), Triple(5, "پ", "پنجشنبه"), Triple(6, "ج", "جمعه"))

val Templates: List<Template> = buildList {
    for (i in 1..4) add(Template("روشن خروجی $i", "${i}1"))
    for (i in 1..4) add(Template("خاموش خروجی $i", "${i}0"))
    for (i in 1..4) add(Template("پالس خروجی $i", "11$i"))
    add(Template("روشن همه", "44")); add(Template("خاموش همه", "00"))
    add(Template("وضعیت رله‌ها", "333")); add(Template("وضعیت ورودی‌ها", "555"))
    add(Template("شارژ همراه اول", "777")); add(Template("شارژ ایرانسل", "888"))
    add(Template("آنتن‌دهی", "999")); add(Template("دما", "TEMP"))
    add(Template("ساعت دستگاه", "9777")); add(Template("۷ شماره آخر", "222"))
    add(Template("گروهی (مثال)", "out#1011"))
}

fun fa(s: String) = buildString { s.forEach { append(if (it in '0'..'9') '\u06F0' + (it - '0') else it) } }

fun fmt(ms: Long, p: String): String = try {
    android.icu.text.SimpleDateFormat(p, android.icu.util.ULocale("fa_IR@calendar=persian")).format(Date(ms))
} catch (e: Throwable) {
    java.text.SimpleDateFormat(p, Locale.US).format(Date(ms))
}

fun epochDay(ms: Long): Long = (ms + TimeZone.getDefault().getOffset(ms)) / 86_400_000L

fun nextTrigger(s: Schedule, now: Long = System.currentTimeMillis()): Long? {
    if (!s.enabled || (!s.everyN && s.days.isEmpty())) return null
    val cal = Calendar.getInstance()
    for (d in 0..800) {
        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_YEAR, d)
        cal.set(Calendar.HOUR_OF_DAY, s.hour); cal.set(Calendar.MINUTE, s.minute)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= now) continue
        val ok = if (s.everyN) {
            val diff = epochDay(cal.timeInMillis) - s.anchorDay
            diff >= 0 && diff % s.n.coerceAtLeast(1) == 0L
        } else cal.get(Calendar.DAY_OF_WEEK) in s.days
        if (ok) return cal.timeInMillis
    }
    return null
}

fun Schedule.label(): String? {
    if (!enabled) return null
    val t = fa("%02d:%02d".format(hour, minute))
    val w = if (everyN) when (n) { 1 -> "هر روز"; 2 -> "یک روز در میان"; else -> "هر ${fa(n.toString())} روز" }
    else if (days.size == 7) "هر روز" else Days.filter { it.first in days }.joinToString("، ") { it.third }
    return "$w • $t"
}

fun Item.toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("icon", icon).put("color", color)
    .put("steps", JSONArray().also { a -> steps.forEach { a.put(JSONObject().put("c", it.cmd).put("d", it.delaySec).put("w", it.waitForReply).put("t", it.timeoutSec)) } })
    .put("sch", JSONObject().put("on", schedule.enabled).put("n1", schedule.everyN)
        .put("days", JSONArray(schedule.days.toList())).put("n", schedule.n)
        .put("anc", schedule.anchorDay).put("h", schedule.hour).put("m", schedule.minute))

fun itemFrom(o: JSONObject): Item {
    val sa = o.getJSONArray("steps"); val so = o.getJSONObject("sch"); val da = so.getJSONArray("days")
    return Item(o.getString("id"), o.getString("name"), o.getString("icon"), o.getInt("color"),
        List(sa.length()) { i -> sa.getJSONObject(i).let { Step(it.getString("c"), it.getInt("d"), it.optBoolean("w", false), it.optInt("t", 60)) } },
        Schedule(so.getBoolean("on"), so.getBoolean("n1"), (0 until da.length()).map { da.getInt(it) }.toSet(),
            so.getInt("n"), so.getLong("anc"), so.getInt("h"), so.getInt("m")))
}

fun Entry.toJson(): JSONObject = JSONObject().put("id", id).put("item", itemId ?: "").put("name", itemName)
    .put("cmd", cmd).put("at", at).put("src", source).put("err", error ?: "")
    .put("rep", JSONArray().also { a -> replies.forEach { a.put(JSONObject().put("t", it.at).put("x", it.text)) } })

fun entryFrom(o: JSONObject): Entry {
    val ra = o.getJSONArray("rep")
    return Entry(o.getString("id"), o.getString("item").ifEmpty { null }, o.getString("name"), o.getString("cmd"),
        o.getLong("at"), o.getString("src"), o.getString("err").ifEmpty { null },
        List(ra.length()) { i -> ra.getJSONObject(i).let { Reply(it.getLong("t"), it.getString("x")) } })
}
