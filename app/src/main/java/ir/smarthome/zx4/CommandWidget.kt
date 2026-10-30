package ir.smarthome.zx4

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.RemoteViews

class CommandWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_RUN) return
        Repo.init(context)
        val id = intent.getStringExtra(EXTRA_ITEM) ?: return
        val item = Repo.item(id) ?: return
        if (Repo.phone.isBlank()) {
            Engine.notifyUser(context, item.name, "ابتدا شماره دستگاه را در تنظیمات وارد کنید")
        } else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Engine.notifyUser(context, item.name, "مجوز ارسال پیامک داده نشده است")
        } else {
            Engine.start(context, item, "widget")
        }
        refreshAll(context)
    }

    companion object {
        const val ACTION_RUN = "ir.smarthome.zx4.WIDGET_RUN"
        const val EXTRA_ITEM = "widget_item"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CommandWidgetProvider::class.java))
            ids.forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            Repo.init(context)
            val prefs = context.getSharedPreferences("zx4_widgets", Context.MODE_PRIVATE)
            val selected = prefs.getString("items_$widgetId", "").orEmpty()
                .split(",").filter { it.isNotBlank() }.toSet()
            val chosen = Repo.items.filter { selected.contains(it.id) }
            val views = RemoteViews(context.packageName, R.layout.command_widget)
            views.setTextViewText(R.id.widget_title, "فرمان‌های خانه")
            views.removeAllViews(R.id.widget_items)
            chosen.forEach { item ->
                val row = RemoteViews(context.packageName, R.layout.command_widget_item)
                val running = Repo.activeItemId == item.id
                val busy = Repo.activeItemId != null
                row.setTextViewText(R.id.widget_item_name, item.name)
                row.setTextViewText(R.id.widget_run, if (running) "در حال اجرا" else "اجرا")
                row.setViewVisibility(R.id.widget_loading, if (running) android.view.View.VISIBLE else android.view.View.GONE)
                row.setBoolean(R.id.widget_run, "setEnabled", !busy)
                val click = PendingIntent.getBroadcast(context, (widgetId.toString() + item.id).hashCode(),
                    Intent(context, CommandWidgetProvider::class.java).setAction(ACTION_RUN).putExtra(EXTRA_ITEM, item.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                row.setOnClickPendingIntent(R.id.widget_run, click)
                views.addView(R.id.widget_items, row)
            }
            if (chosen.isEmpty()) views.setTextViewText(R.id.widget_title, "برای این ویجت آیتمی انتخاب نشده")
            manager.updateAppWidget(widgetId, views)
        }
    }
}

class WidgetConfigureActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val checks = mutableListOf<Pair<String, CheckBox>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        Repo.init(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply { text = "آیتم‌های ویجت را انتخاب کنید"; textSize = 18f })
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Repo.items.forEach { item ->
            val check = CheckBox(this).apply { text = item.name; isChecked = true }
            checks.add(item.id to check)
            list.addView(check)
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Button(this).apply {
            text = "ذخیره ویجت"
            setOnClickListener {
                val ids = checks.filter { it.second.isChecked }.joinToString(",") { it.first }
                getSharedPreferences("zx4_widgets", Context.MODE_PRIVATE).edit().putString("items_$widgetId", ids).apply()
                AppWidgetManager.getInstance(this@WidgetConfigureActivity).updateAppWidget(widgetId,
                    RemoteViews(packageName, R.layout.command_widget))
                CommandWidgetProvider.refreshAll(this@WidgetConfigureActivity)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                finish()
            }
        })
        setContentView(root)
    }
}
