package ir.smarthome.zx4

import android.app.Application
import android.os.Build
import android.telephony.SmsManager
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Item(
    val id: String,
    val name: String,
    val icon: String,
    val color: Int,
    val commands: List<String>,
)

data class Template(val label: String, val cmd: String)

/** دستورهای آماده بر اساس راهنمای دستگاه */
val Templates: List<Template> = buildList {
    for (i in 1..4) add(Template("روشن خروجی $i", "${i}1"))
    for (i in 1..4) add(Template("خاموش خروجی $i", "${i}0"))
    for (i in 1..4) add(Template("پالس خروجی $i", "11$i"))
    add(Template("روشن همه", "44"))
    add(Template("خاموش همه", "00"))
    add(Template("وضعیت رله‌ها", "333"))
    add(Template("وضعیت ورودی‌ها", "555"))
    add(Template("شارژ همراه اول", "777"))
    add(Template("شارژ ایرانسل", "888"))
    add(Template("آنتن‌دهی", "999"))
    add(Template("دما", "TEMP"))
    add(Template("ساعت دستگاه", "9777"))
    add(Template("۷ شماره آخر", "222"))
    add(Template("گروهی (مثال)", "out#1011"))
}

class MainVM(app: Application) : AndroidViewModel(app) {
    private val sp = app.getSharedPreferences("zx4", 0)

    val items = mutableStateListOf<Item>()
    var phone by mutableStateOf(sp.getString("phone", "") ?: "")
    var delaySec by mutableIntStateOf(sp.getInt("delay", 20))
    var confirm by mutableStateOf(sp.getBoolean("confirm", false))
    var status by mutableStateOf<String?>(null)
    var sending by mutableStateOf(false)
    private var job: Job? = null

    init { load() }

    private fun load() {
        val s = sp.getString("items", null)
        if (s == null) {
            items.addAll(defaults())
            return
        }
        val a = JSONArray(s)
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val c = o.getJSONArray("cmds")
            items.add(
                Item(o.getString("id"), o.getString("name"), o.getString("icon"),
                    o.getInt("color"), List(c.length()) { c.getString(it) })
            )
        }
    }

    private fun defaults(): List<Item> = buildList {
        for (i in 1..4) {
            add(Item(UUID.randomUUID().toString(), "روشن شدن خروجی $i", "bolt", 0, listOf("${i}1")))
            add(Item(UUID.randomUUID().toString(), "خاموش شدن خروجی $i", "power", 3, listOf("${i}0")))
        }
        add(Item(UUID.randomUUID().toString(), "خاموش کردن همه", "home", 4, listOf("00")))
        add(Item(UUID.randomUUID().toString(), "وضعیت رله‌ها", "sensors", 1, listOf("333")))
        add(Item(UUID.randomUUID().toString(), "شارژ سیم‌کارت", "bell", 2, listOf("777")))
    }

    private fun persist() {
        val a = JSONArray()
        items.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon)
                .put("color", it.color).put("cmds", JSONArray(it.commands)))
        }
        sp.edit().putString("items", a.toString()).apply()
    }

    fun savePrefs() {
        sp.edit().putString("phone", phone.trim()).putInt("delay", delaySec)
            .putBoolean("confirm", confirm).apply()
    }

    fun upsert(item: Item) {
        val i = items.indexOfFirst { it.id == item.id }
        if (i >= 0) items[i] = item else items.add(item)
        persist()
    }

    fun delete(id: String) {
        items.removeAll { it.id == id }
        persist()
    }

    fun cancel() { job?.cancel() }

    fun run(item: Item) {
        if (phone.isBlank()) { status = "ابتدا شماره سیم‌کارت دستگاه را در تنظیمات وارد کنید"; return }
        if (sending) { status = "ارسال قبلی هنوز تمام نشده است"; return }
        job = viewModelScope.launch {
            sending = true
            try {
                val n = item.commands.size
                item.commands.forEachIndexed { i, c ->
                    status = "ارسال ${i + 1} از $n: $c"
                    send(c)
                    if (i < n - 1) {
                        for (s in delaySec downTo 1) {
                            status = "دستور بعدی تا $s ثانیه دیگر ارسال می‌شود"
                            delay(1000)
                        }
                    }
                }
                status = "«${item.name}» ارسال شد ✓"
            } catch (e: CancellationException) {
                status = "ارسال لغو شد"
            } catch (e: Exception) {
                status = "خطا در ارسال: ${e.message}"
            } finally {
                sending = false
            }
        }
    }

    private fun send(text: String) {
        val ctx = getApplication<Application>()
        val sm = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
        sm.sendTextMessage(phone.trim(), null, text, null, null)
    }
}
