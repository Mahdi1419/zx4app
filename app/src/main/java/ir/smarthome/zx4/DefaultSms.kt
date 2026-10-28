package ir.smarthome.zx4

import android.app.Activity
import android.app.Service
import android.app.role.RoleManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Telephony

fun isDefaultSms(ctx: Context): Boolean = Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName

private val smsComponents = listOf(SmsDeliverReceiver::class.java, MmsReceiver::class.java,
    RespondService::class.java, ComposeActivity::class.java)

fun setSmsComponents(ctx: Context, enabled: Boolean) {
    val st = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    smsComponents.forEach { ctx.packageManager.setComponentEnabledSetting(ComponentName(ctx, it), st, PackageManager.DONT_KILL_APP) }
}

fun syncDefaultSms(ctx: Context) {
    Repo.isDefaultSms = isDefaultSms(ctx)
    if (!Repo.isDefaultSms) setSmsComponents(ctx, false)
}

fun defaultSmsIntent(ctx: Context): Intent =
    if (Build.VERSION.SDK_INT >= 29) ctx.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS)
    else Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, ctx.packageName)

/** در حالت SMS پیش‌فرض: پیام ماژول فقط داخل برنامه می‌ماند؛ بقیه پیامک‌ها در صندوق سیستم ذخیره می‌شوند. */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return
        val from = msgs[0].originatingAddress
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        if (Repo.isDevice(from)) {
            Repo.onReply(ctx, body)
        } else {
            runCatching {
                ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, from); put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, msgs[0].timestampMillis); put(Telephony.Sms.READ, 0)
                })
            }
            Engine.notifyUser(ctx, from ?: "پیامک جدید", body)
        }
    }
}

class MmsReceiver : BroadcastReceiver() { override fun onReceive(ctx: Context, intent: Intent) {} }

class RespondService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { stopSelf(); return START_NOT_STICKY }
}

class ComposeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); finish() }
}
