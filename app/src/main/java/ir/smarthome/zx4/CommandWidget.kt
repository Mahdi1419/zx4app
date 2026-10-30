package ir.smarthome.zx4

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

class CommandWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Repo.init(context)
        ids.forEach { update(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_RUN) return
        Repo.init(context)
        val itemId = intent.getStringExtra(EXTRA_ITEM) ?: return
        val item = Repo.item(itemId) ?: return
        when {
            Repo.phone.isBlank() -> Engine.notifyUser(context, item.name, "ابتدا شماره دستگاه را در تنظیمات وارد کنید")
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED ->
                Engine.notifyUser(context, item.name, "مجوز ارسال پیامک را در برنامه فعال کنید")
            Repo.activeItemId != null -> Engine.notifyUser(context, item.name, "یک فرمان دیگر هنوز در حال اجراست")
            !Engine.start(context, item, "widget") -> Engine.notifyUser(context, item.name, Repo.status ?: "اجرای فرمان شروع نشد")
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
            val selectedIds = prefs.getString("items_$widgetId", null)?.split(",")?.filter { it.isNotBlank() }?.toSet()
            val mode = prefs.getString("mode_$widgetId", "single") ?: "single"
            val chosen = Repo.items.filter { selectedIds?.contains(it.id) == true }
                .let { if (mode == "single") it.take(1) else it }
            val views = android.widget.RemoteViews(context.packageName, R.layout.command_widget)
            views.setTextViewText(R.id.widget_title, if (mode == "single") "فرمان سریع" else "فرمان‌های خانه")
            views.removeAllViews(R.id.widget_items)
            if (chosen.isEmpty()) {
                views.setTextViewText(R.id.widget_empty, "برای تنظیم، ویجت را لمس کنید")
                views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)
                chosen.forEach { item ->
                    val row = android.widget.RemoteViews(context.packageName, R.layout.command_widget_item)
                    val running = Repo.activeItemId == item.id && Repo.pending.containsKey(item.id)
                    val busy = Repo.activeItemId != null
                    row.setTextViewText(R.id.widget_item_name, item.name)
                    row.setTextViewText(R.id.widget_run, if (running) "در حال اجرا" else "اجرا")
                    row.setViewVisibility(R.id.widget_loading, if (running) android.view.View.VISIBLE else android.view.View.GONE)
                    row.setBoolean(R.id.widget_run, "setEnabled", !busy)
                    row.setInt(R.id.widget_run, "setBackgroundResource",
                        if (busy && !running) R.drawable.widget_action_disabled else R.drawable.widget_action_bg)
                    val click = PendingIntent.getBroadcast(
                        context, (widgetId.toString() + item.id).hashCode(),
                        Intent(context, CommandWidgetProvider::class.java).setAction(ACTION_RUN).putExtra(EXTRA_ITEM, item.id),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    row.setOnClickPendingIntent(R.id.widget_run, click)
                    views.addView(R.id.widget_items, row)
                }
            }
            manager.updateAppWidget(widgetId, views)
        }
    }
}

class WidgetConfigureActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val checks = mutableListOf<Pair<String, CheckBox>>()
    private lateinit var modeGroup: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        Repo.init(this)
        val prefs = getSharedPreferences("zx4_widgets", Context.MODE_PRIVATE)
        val existing = prefs.getString("items_$widgetId", null)?.split(",")?.toSet()
        val savedMode = prefs.getString("mode_$widgetId", "single") ?: "single"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(Color.rgb(247, 248, 250))
        }
        root.addView(TextView(this).apply {
            text = "تنظیم ویجت"
            textSize = 22f
            setTextColor(Color.rgb(25, 29, 36))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "نوع ویجت را انتخاب کن و فرمان‌هایی را که می‌خواهی روی صفحه اصلی باشند مشخص کن."
            textSize = 14f
            setTextColor(Color.rgb(95, 102, 112))
            setPadding(0, dp(8), 0, dp(14))
        })
        modeGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val single = RadioButton(this).apply { text = "تک‌دکمه‌ای"; id = 1 }
        val multi = RadioButton(this).apply { text = "چنددکمه‌ای"; id = 2 }
        modeGroup.addView(single); modeGroup.addView(multi)
        modeGroup.check(if (savedMode == "multi") 2 else 1)
        root.addView(modeGroup)

        root.addView(TextView(this).apply {
            text = "انتخاب فرمان‌ها"
            textSize = 16f
            setTextColor(Color.rgb(25, 29, 36))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(8))
        })
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Repo.items.forEachIndexed { index, item ->
            val check = CheckBox(this).apply {
                text = item.name
                textSize = 15f
                setTextColor(Color.rgb(35, 40, 48))
                isChecked = existing?.contains(item.id) ?: (index == 0)
                setOnCheckedChangeListener { _, checked ->
                    if (checked && modeGroup.checkedRadioButtonId == 1) {
                        checks.forEach { (id, other) -> if (id != item.id) other.isChecked = false }
                    }
                }
            }
            checks.add(item.id to check)
            list.addView(check)
        }
        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == 1) {
                var kept = false
                checks.forEach { (_, check) ->
                    if (check.isChecked && !kept) kept = true else if (check.isChecked) check.isChecked = false
                }
            }
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Button(this).apply {
            text = "ذخیره و نمایش ویجت"
            setOnClickListener {
                val selected = checks.filter { it.second.isChecked }.map { it.first }
                if (selected.isEmpty()) {
                    android.widget.Toast.makeText(this@WidgetConfigureActivity, "حداقل یک فرمان انتخاب کن", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val mode = if (modeGroup.checkedRadioButtonId == 2) "multi" else "single"
                val ids = if (mode == "single") selected.take(1) else selected
                prefs.edit().putString("items_$widgetId", ids.joinToString(",")).putString("mode_$widgetId", mode).apply()
                CommandWidgetProvider.refreshAll(this@WidgetConfigureActivity)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                finish()
            }
        })
        setContentView(root)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
