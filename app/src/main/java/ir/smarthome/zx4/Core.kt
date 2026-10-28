package ir.smarthome.zx4

import android.app.*
import android.content.*
import android.content.SharedPreferences
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.compose.runtime.*
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        // Re-arm persisted schedules and in-flight steps whenever Android recreates the app process.
        Engine.scheduleAll(this)
        Engine.restorePending(this)
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("replies", "پاسخ دستگاه", NotificationManager.IMPORTANCE_HIGH))
        }
    }
}

object Repo {
    private var sp: SharedPreferences? = null
    val items = mutableStateListOf<Item>()
    val history = mutableStateListOf<Entry>()
    val pending = mutableStateMapOf<String, Pending>()
    var phone by mutableStateOf("")
    var confirm by mutableStateOf(false)
    var status by mutableStateOf<String?>(null)
    var isDefaultSms by mutableStateOf(false)
    var gapSec by mutableIntStateOf(20)
    var lastSent = 0L
    var themeMode by mutableIntStateOf(0)
    var accent by mutableIntStateOf(0)
    var appTitle by mutableStateOf("کنترل ماژول پیامکی")
    var deviceName by mutableStateOf("دستگاه")
    var shortcutName by mutableStateOf("کنترل ماژول پیامکی")
    var shortcutIconUri by mutableStateOf("")

    fun init(ctx: Context) {
        if (sp != null) return
        val s = ctx.applicationContext.getSharedPreferences("zx4", 0); sp = s
        phone = s.getString("phone", "") ?: ""; confirm = s.getBoolean("confirm", false)
        gapSec = s.getInt("gap", 20).coerceIn(1, 300); lastSent = s.getLong("last", 0L)
        themeMode = s.getInt("theme", 0); accent = s.getInt("accent", 0)
        appTitle = s.getString("title", null) ?: "خانه هوشمند"; deviceName = s.getString("dname", null) ?: "دستگاه"
        shortcutName = s.getString("shortcut_name", null) ?: appTitle.ifBlank { "کنترل ZX4" }
        shortcutIconUri = s.getString("shortcut_icon_uri", "") ?: ""
        val raw = s.getString("items2", null)
        if (raw == null) items.addAll(defaults()) else runCatching {
            val a = JSONArray(raw); for (i in 0 until a.length()) items.add(itemFrom(a.getJSONObject(i)))
        }
        runCatching {
            s.getString("hist", null)?.let { val a = JSONArray(it); for (i in 0 until a.length()) history.add(entryFrom(a.getJSONObject(i))) }
        }
        runCatching {
            s.getString("pend", null)?.let { val o = JSONObject(it)
                o.keys().forEach { k -> val p = o.getJSONObject(k); pending[k] = Pending(p.getInt("i"), p.getLong("a")) } }
        }
    }

    private fun defaults() = listOf(
        Item(uid(), "روشن کردن همه", "bolt", 0, listOf(Step("44", 0))),
        Item(uid(), "خاموش کردن همه", "power", 3, listOf(Step("00", 0))),
        Item(uid(), "وضعیت رله‌ها", "sensors", 1, listOf(Step("333", 0))),
        Item(uid(), "پمپ آب (۱۰ دقیقه)", "water", 4, listOf(Step("21", 600), Step("20", 0))),
        Item(uid(), "شارژ سیم‌کارت", "bell", 2, listOf(Step("777", 0))),
    )

    fun uid() = UUID.randomUUID().toString()
    fun item(id: String) = items.firstOrNull { it.id == id }
    private fun ed() = sp!!.edit()

    fun savePrefs() { ed().putString("phone", phone.trim()).putBoolean("confirm", confirm).putInt("gap", gapSec)
        .putInt("theme", themeMode).putInt("accent", accent).putString("title", appTitle).putString("dname", deviceName)
        .putString("shortcut_name", shortcutName).putString("shortcut_icon_uri", shortcutIconUri).apply() }
    fun waitMs(now: Long = System.currentTimeMillis()): Long = (lastSent + gapSec * 1000L - now).coerceAtLeast(0L)
    fun markSent(t: Long) { lastSent = t; ed().putLong("last", t).apply() }
    private fun saveItems() { ed().putString("items2", JSONArray().also { a -> items.forEach { a.put(it.toJson()) } }.toString()).apply() }
    private fun saveHistory() { ed().putString("hist", JSONArray().also { a -> history.forEach { a.put(it.toJson()) } }.toString()).apply() }
    private fun savePending() {
        val o = JSONObject(); pending.forEach { (k, v) -> o.put(k, JSONObject().put("i", v.idx).put("a", v.at)) }
        ed().putString("pend", o.toString()).apply()
    }

    fun upsert(item: Item) { val i = items.indexOfFirst { it.id == item.id }; if (i >= 0) items[i] = item else items.add(item); saveItems() }
    fun delete(id: String) { items.removeAll { it.id == id }; saveItems() }
    fun setPending(id: String, p: Pending) { pending[id] = p; savePending() }
    fun clearPending(id: String) { if (pending.remove(id) != null) savePending() }
    fun clearHistory() { history.clear(); saveHistory() }

    fun addEntry(e: Entry) { history.add(0, e); while (history.size > 300) history.removeAt(history.lastIndex); saveHistory() }

    private fun tail(s: String) = s.filter { it.isDigit() }.takeLast(10)
    fun isDevice(from: String?) = phone.isNotBlank() && from != null && tail(from) == tail(phone)

    /** پاسخ دستگاه: اگر در ۶۰ ثانیه اخیر پیامکی ارسال شده باشد به همان وصل می‌شود */
    fun onReply(ctx: Context, text: String) {
        val now = System.currentTimeMillis()
        val i = history.indexOfFirst { it.source != "device" && it.error == null && now - it.at <= 60_000 }
        val title: String
        if (i >= 0) {
            val e = history[i]; title = e.itemName
            history[i] = e.copy(replies = e.replies + Reply(now, text))
        } else {
            title = deviceName.ifBlank { "پیام دستگاه" }
            history.add(0, Entry(uid(), null, title, "", now, "device", null, listOf(Reply(now, text))))
        }
        saveHistory(); Engine.notifyUser(ctx, title, text)
    }
}

object Engine {
    private const val STEP = "ir.smarthome.zx4.STEP"
    private const val SCHED = "ir.smarthome.zx4.SCHED"

    private fun pi(ctx: Context, action: String, itemId: String, idx: Int, src: String): PendingIntent =
        PendingIntent.getBroadcast(ctx, (action + itemId).hashCode(),
            Intent(ctx, AlarmReceiver::class.java).setAction(action)
                .putExtra("item", itemId).putExtra("idx", idx).putExtra("src", src),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    private fun setAt(ctx: Context, at: Long, p: PendingIntent) {
        val a = am(ctx)
        if (Build.VERSION.SDK_INT < 31 || a.canScheduleExactAlarms()) a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        else a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
    }

    fun send(ctx: Context, phone: String, text: String) {
        require(phone.isNotBlank()) { "شماره دستگاه تنظیم نشده" }
        val sm = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
        sm.sendTextMessage(phone.trim(), null, text, null, null)
    }

    fun start(ctx: Context, item: Item, source: String) { cancelRun(ctx, item.id); runStep(ctx, item.id, 0, source) }

    @Synchronized
    fun runStep(ctx: Context, itemId: String, idx: Int, source: String) {
        val item = Repo.item(itemId) ?: return
        val step = item.steps.getOrNull(idx) ?: return
        val now = System.currentTimeMillis()
        val wait = Repo.waitMs(now)
        if (wait > 0) { // فاصله حداقلی بین پیامک‌ها هنوز تمام نشده؛ همین مرحله بعداً اجرا می‌شود
            val at = now + wait
            Repo.setPending(itemId, Pending(idx, at))
            setAt(ctx, at, pi(ctx, STEP, itemId, idx, source))
            return
        }
        val err = try { send(ctx, Repo.phone, step.cmd); null } catch (e: Exception) { e.message ?: "خطا" }
        if (err == null) Repo.markSent(System.currentTimeMillis())
        Repo.addEntry(Entry(Repo.uid(), item.id, item.name, step.cmd, now, source, err, emptyList()))
        if (err == null && idx + 1 < item.steps.size) {
            val at = now + maxOf(step.delaySec, Repo.gapSec) * 1000L
            Repo.setPending(itemId, Pending(idx + 1, at))
            setAt(ctx, at, pi(ctx, STEP, itemId, idx + 1, source))
        } else Repo.clearPending(itemId)
        if (err != null) notifyUser(ctx, item.name, "ارسال ناموفق: $err")
    }

    fun cancelRun(ctx: Context, itemId: String) { am(ctx).cancel(pi(ctx, STEP, itemId, 0, "")); Repo.clearPending(itemId) }

    fun schedule(ctx: Context, item: Item) {
        val p = pi(ctx, SCHED, item.id, 0, "schedule")
        am(ctx).cancel(p)
        nextTrigger(item.schedule)?.let { setAt(ctx, it, p) }
    }

    fun scheduleAll(ctx: Context) = Repo.items.forEach { schedule(ctx, it) }

    /** Rebuild alarms for persisted multi-step work after reboot/process recreation. */
    fun restorePending(ctx: Context) {
        val now = System.currentTimeMillis()
        Repo.pending.toMap().forEach { (id, p) ->
            val item = Repo.item(id)
            if (item == null || item.steps.getOrNull(p.idx) == null) {
                Repo.clearPending(id)
            } else {
                setAt(ctx, maxOf(now + 1_000L, p.at), pi(ctx, STEP, id, p.idx, "schedule"))
            }
        }
    }

    fun remove(ctx: Context, id: String) { cancelRun(ctx, id); am(ctx).cancel(pi(ctx, SCHED, id, 0, "schedule")) }

    fun onAlarm(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("item") ?: return
        when (intent.action) {
            STEP -> runStep(ctx, id, intent.getIntExtra("idx", 0), intent.getStringExtra("src") ?: "schedule")
            SCHED -> Repo.item(id)?.let { start(ctx, it, "schedule"); schedule(ctx, it) }
        }
    }

    fun notifyUser(ctx: Context, title: String, text: String) {
        try {
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, "replies")
                .setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle(title).setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
            ctx.getSystemService(NotificationManager::class.java).notify((System.nanoTime() % 100000).toInt(), n)
        } catch (_: Exception) {}
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) { Repo.init(ctx); Engine.onAlarm(ctx, intent) }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        // Wall-clock and timezone changes invalidate future AlarmManager trigger times.
        Engine.scheduleAll(ctx)
        Engine.restorePending(ctx)
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        if (isDefaultSms(ctx)) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty() || !Repo.isDevice(msgs[0].originatingAddress)) return
        Repo.onReply(ctx, msgs.joinToString("") { it.messageBody ?: "" })
    }
}
