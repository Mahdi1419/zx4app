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
    var themeMode by mutableIntStateOf(0)
    var accent by mutableIntStateOf(0)
    var appTitle by mutableStateOf("خانه هوشمند")
    var deviceName by mutableStateOf("دستگاه")
    var activeItemId by mutableStateOf<String?>(null)

    fun init(ctx: Context) {
        if (sp != null) return
        val s = ctx.applicationContext.getSharedPreferences("zx4", 0); sp = s
        phone = s.getString("phone", "") ?: ""
        confirm = s.getBoolean("confirm", false)
        themeMode = s.getInt("theme", 0); accent = s.getInt("accent", 0)
        appTitle = s.getString("title", null) ?: "خانه هوشمند"
        deviceName = s.getString("dname", null) ?: "دستگاه"
        activeItemId = s.getString("active_item", null)
        val raw = s.getString("items2", null)
        if (raw == null) items.addAll(defaults()) else runCatching {
            val a = JSONArray(raw); for (i in 0 until a.length()) items.add(itemFrom(a.getJSONObject(i)))
        }
        runCatching {
            s.getString("hist", null)?.let { val a = JSONArray(it); for (i in 0 until a.length()) history.add(entryFrom(a.getJSONObject(i))) }
        }
        runCatching {
            s.getString("pend", null)?.let { val o = JSONObject(it)
                o.keys().forEach { k ->
                    val p = o.getJSONObject(k)
                    pending[k] = Pending(p.getInt("i"), p.getLong("a"), p.optBoolean("w", false), p.optString("s", "manual"))
                }
            }
        }
        if (activeItemId == null && pending.isNotEmpty()) activeItemId = pending.keys.first()
    }

    private fun defaults() = listOf(
        Item(uid(), "روشن کردن همه", "bolt", 0, listOf(Step("44", 0))),
        Item(uid(), "خاموش کردن همه", "power", 3, listOf(Step("00", 0))),
        Item(uid(), "وضعیت رله‌ها", "sensors", 1, listOf(Step("333", 0))),
        Item(uid(), "پمپ آب (۱۰ دقیقه)", "water", 4, listOf(Step("21", 0), Step("20", 600))),
        Item(uid(), "شارژ سیم‌کارت", "bell", 2, listOf(Step("777", 0))),
    )

    fun uid() = UUID.randomUUID().toString()
    fun item(id: String) = items.firstOrNull { it.id == id }
    private fun ed() = sp!!.edit()

    fun savePrefs() {
        ed().putString("phone", phone.trim()).putBoolean("confirm", confirm)
            .putInt("theme", themeMode).putInt("accent", accent)
            .putString("title", appTitle).putString("dname", deviceName).apply()
    }
    private fun saveItems() { ed().putString("items2", JSONArray().also { a -> items.forEach { a.put(it.toJson()) } }.toString()).apply() }
    private fun saveHistory() { ed().putString("hist", JSONArray().also { a -> history.forEach { a.put(it.toJson()) } }.toString()).apply() }
    private fun savePending() {
        val o = JSONObject()
        pending.forEach { (k, v) -> o.put(k, JSONObject().put("i", v.idx).put("a", v.at).put("w", v.waitingReply).put("s", v.source)) }
        ed().putString("pend", o.toString()).putString("active_item", activeItemId).apply()
    }
    fun setActive(id: String?) { activeItemId = id; ed().putString("active_item", id).apply() }
    fun upsert(item: Item) { val i = items.indexOfFirst { it.id == item.id }; if (i >= 0) items[i] = item else items.add(item); saveItems() }
    fun delete(id: String) { items.removeAll { it.id == id }; saveItems() }
    fun setPending(id: String, p: Pending) { pending[id] = p; savePending() }
    fun clearPending(id: String) { pending.remove(id); savePending() }
    fun clearHistory() { history.clear(); saveHistory() }
    fun addEntry(e: Entry) { history.add(0, e); while (history.size > 300) history.removeAt(history.lastIndex); saveHistory() }

    private fun tail(s: String) = s.filter { it.isDigit() }.takeLast(10)
    fun isDevice(from: String?) = phone.isNotBlank() && from != null && tail(from) == tail(phone)

    fun onReply(ctx: Context, text: String) {
        val now = System.currentTimeMillis()
        val active = activeItemId
        val wait = active?.let { pending[it] }?.takeIf { it.waitingReply }
        val i = if (wait != null) history.indexOfFirst { it.itemId == active && it.source != "device" && it.error == null }
            else history.indexOfFirst { it.source != "device" && it.error == null && now - it.at <= 60_000 }
        val title: String
        if (i >= 0) {
            val e = history[i]; title = e.itemName
            history[i] = e.copy(replies = e.replies + Reply(now, text))
        } else {
            title = deviceName.ifBlank { "پیام دستگاه" }
            history.add(0, Entry(uid(), null, title, "", now, "device", null, listOf(Reply(now, text))))
        }
        saveHistory()
        if (wait != null && active != null) Engine.onExpectedReply(ctx, active, wait.idx)
        Engine.notifyUser(ctx, title, text)
    }
}

object Engine {
    private const val STEP = "ir.smarthome.zx4.STEP"
    private const val SCHED = "ir.smarthome.zx4.SCHED"
    private const val TIMEOUT = "ir.smarthome.zx4.TIMEOUT"

    private fun pi(ctx: Context, action: String, itemId: String, idx: Int, src: String): PendingIntent =
        PendingIntent.getBroadcast(ctx, (action + itemId).hashCode(),
            Intent(ctx, AlarmReceiver::class.java).setAction(action)
                .putExtra("item", itemId).putExtra("idx", idx).putExtra("src", src),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    private fun setAt(ctx: Context, at: Long, p: PendingIntent) {
        val alarm = am(ctx)
        if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    fun send(ctx: Context, phone: String, text: String) {
        require(phone.isNotBlank()) { "شماره دستگاه تنظیم نشده" }
        val sm = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
        sm.sendTextMessage(phone.trim(), null, text, null, null)
    }

    @Synchronized
    fun start(ctx: Context, item: Item, source: String): Boolean {
        if (Repo.activeItemId != null) {
            Repo.status = "فرمان «${Repo.item(Repo.activeItemId!!)?.name ?: "دیگر"}» هنوز در حال اجراست"
            return false
        }
        Repo.setActive(item.id)
        CommandWidgetProvider.refreshAll(ctx)
        runStep(ctx, item.id, 0, source)
        CommandWidgetProvider.refreshAll(ctx)
        return true
    }

    @Synchronized
    fun runStep(ctx: Context, itemId: String, idx: Int, source: String) {
        val item = Repo.item(itemId) ?: run { finish(itemId); return }
        val step = item.steps.getOrNull(idx) ?: run { finish(itemId); return }
        if (Repo.activeItemId != itemId) {
            if (Repo.activeItemId != null) return
            Repo.setActive(itemId)
        }
        Repo.pending.remove(itemId)
        Repo.setPending(itemId, Pending(idx, System.currentTimeMillis(), false, source))
        val now = System.currentTimeMillis()
        val err = try { send(ctx, Repo.phone, step.cmd); null } catch (e: Exception) { e.message ?: "خطا" }
        Repo.addEntry(Entry(Repo.uid(), item.id, item.name, step.cmd, now, source, err, emptyList()))
        if (err != null) {
            finish(itemId)
            notifyUser(ctx, item.name, "ارسال ناموفق: $err")
            CommandWidgetProvider.refreshAll(ctx)
            return
        }
        if (step.waitForReply) {
            val timeoutAt = System.currentTimeMillis() + step.timeoutSec.coerceIn(1, 86_400) * 1000L
            Repo.setPending(itemId, Pending(idx, timeoutAt, true, source))
            setAt(ctx, timeoutAt, pi(ctx, TIMEOUT, itemId, idx, source))
            CommandWidgetProvider.refreshAll(ctx)
        } else {
            advance(ctx, item, idx, source)
            CommandWidgetProvider.refreshAll(ctx)
        }
    }

    @Synchronized
    private fun advance(ctx: Context, item: Item, idx: Int, source: String) {
        val next = idx + 1
        if (next >= item.steps.size) { finish(item.id); return }
        val at = System.currentTimeMillis() + item.steps[next].delaySec.coerceAtLeast(0) * 1000L
        Repo.setPending(item.id, Pending(next, at, false, source))
        setAt(ctx, at, pi(ctx, STEP, item.id, next, source))
        CommandWidgetProvider.refreshAll(ctx)
    }

    @Synchronized
    fun onExpectedReply(ctx: Context, itemId: String, idx: Int) {
        val p = Repo.pending[itemId] ?: return
        if (!p.waitingReply || p.idx != idx || Repo.activeItemId != itemId) return
        am(ctx).cancel(pi(ctx, TIMEOUT, itemId, idx, p.source))
        val item = Repo.item(itemId) ?: run { finish(itemId); return }
        Repo.clearPending(itemId)
        advance(ctx, item, idx, p.source)
        CommandWidgetProvider.refreshAll(ctx)
    }

    @Synchronized
    private fun onTimeout(ctx: Context, itemId: String, idx: Int, source: String) {
        val p = Repo.pending[itemId] ?: return
        if (!p.waitingReply || p.idx != idx || Repo.activeItemId != itemId) return
        Repo.clearPending(itemId)
        notifyUser(ctx, Repo.item(itemId)?.name ?: "فرمان", "مهلت انتظار برای پاسخ تمام شد")
        Repo.item(itemId)?.let { advance(ctx, it, idx, source) } ?: finish(itemId)
        CommandWidgetProvider.refreshAll(ctx)
    }

    @Synchronized
    fun cancelRun(ctx: Context, itemId: String) {
        am(ctx).cancel(pi(ctx, STEP, itemId, 0, ""))
        Repo.pending[itemId]?.let { p -> am(ctx).cancel(pi(ctx, TIMEOUT, itemId, p.idx, p.source)) }
        Repo.clearPending(itemId)
        if (Repo.activeItemId == itemId) Repo.setActive(null)
        CommandWidgetProvider.refreshAll(ctx)
    }

    private fun finish(itemId: String) {
        Repo.clearPending(itemId)
        if (Repo.activeItemId == itemId) Repo.setActive(null)
    }

    fun schedule(ctx: Context, item: Item) {
        val p = pi(ctx, SCHED, item.id, 0, "schedule")
        am(ctx).cancel(p)
        nextTrigger(item.schedule)?.let { setAt(ctx, it, p) }
    }

    fun scheduleAll(ctx: Context) = Repo.items.forEach { schedule(ctx, it) }

    fun restorePending(ctx: Context) {
        val now = System.currentTimeMillis()
        Repo.pending.toMap().forEach { (id, p) ->
            val item = Repo.item(id)
            if (item == null || item.steps.getOrNull(p.idx) == null) {
                finish(id)
            } else if (p.waitingReply) {
                if (p.at <= now) onTimeout(ctx, id, p.idx, p.source)
                else setAt(ctx, p.at, pi(ctx, TIMEOUT, id, p.idx, p.source))
            } else {
                setAt(ctx, maxOf(now + 1_000L, p.at), pi(ctx, STEP, id, p.idx, p.source))
            }
        }
    }

    fun remove(ctx: Context, id: String) {
        cancelRun(ctx, id)
        am(ctx).cancel(pi(ctx, SCHED, id, 0, "schedule"))
    }

    fun onAlarm(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("item") ?: return
        val idx = intent.getIntExtra("idx", 0)
        val src = intent.getStringExtra("src") ?: "schedule"
        when (intent.action) {
            STEP -> runStep(ctx, id, idx, src)
            TIMEOUT -> onTimeout(ctx, id, idx, src)
            SCHED -> {
                Repo.item(id)?.let { item ->
                    if (Repo.activeItemId == null) {
                        if (start(ctx, item, "schedule")) schedule(ctx, item)
                    } else {
                        // Keep the due run queued; retry shortly after the current global run finishes.
                        setAt(ctx, System.currentTimeMillis() + 30_000L, pi(ctx, SCHED, id, 0, "schedule"))
                    }
                }
            }
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
