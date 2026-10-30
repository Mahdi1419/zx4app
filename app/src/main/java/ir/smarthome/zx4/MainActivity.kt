@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ir.smarthome.zx4

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

val Palette = listOf(0xFF0F766E, 0xFF2563EB, 0xFFD97706, 0xFFDC2626, 0xFF7C3AED, 0xFF059669)

val IconMap: Map<String, ImageVector> = linkedMapOf(
    "bolt" to Icons.Filled.Bolt, "bulb" to Icons.Filled.Lightbulb, "power" to Icons.Filled.PowerSettingsNew,
    "lock" to Icons.Filled.Lock, "unlock" to Icons.Filled.LockOpen, "home" to Icons.Filled.Home,
    "fan" to Icons.Filled.Air, "water" to Icons.Filled.WaterDrop, "temp" to Icons.Filled.Thermostat,
    "sensors" to Icons.Filled.Sensors, "bell" to Icons.Filled.Notifications,
)

class MainActivity : ComponentActivity() {
    override fun onResume() { super.onResume(); syncDefaultSms(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Engine.scheduleAll(this)
        setContent {
            val dark = Repo.themeMode == 2 || (Repo.themeMode == 0 && isSystemInDarkTheme())
            DisposableEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            AppTheme(Repo.themeMode, Repo.accent) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface
                ) { Root() }
            }
        }
    }
}

private fun perms() = buildList {
    add(Manifest.permission.SEND_SMS); add(Manifest.permission.RECEIVE_SMS)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}

@Composable
fun Root() {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Item?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var toConfirm by remember { mutableStateOf<Item?>(null) }
    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) { perms().filter { !granted(it) }.let { if (it.isNotEmpty()) launcher.launch(it.toTypedArray()) } }

    fun trigger(item: Item) {
        if (Repo.phone.isBlank()) { Repo.status = "ابتدا شماره سیم‌کارت دستگاه را در تنظیمات وارد کنید"; return }
        if (!granted(Manifest.permission.SEND_SMS)) {
            Repo.status = "اجازه ارسال پیامک لازم است"; launcher.launch(perms().toTypedArray()); return
        }
        if (Repo.activeItemId != null) { Repo.status = "یک فرمان دیگر هنوز در حال اجراست"; return }
        if (Engine.start(ctx, item, "manual")) {
            Repo.status = "«${item.name}» شروع شد" + if (item.steps.size > 1) " (${fa(item.steps.size.toString())} مرحله)" else ""
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 10.dp)) {
                    Surface(shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
                        shadowElevation = 12.dp) {
                        Row(Modifier.fillMaxWidth().padding(7.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("خانه" to Icons.Filled.Home, "تاریخچه" to Icons.Filled.History, "تنظیمات" to Icons.Filled.Settings)
                                .forEachIndexed { i, (l, ic) ->
                                    val selected = tab == i
                                    Surface(onClick = { tab = i }, shape = RoundedCornerShape(20.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                                        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)) {
                                        Column(Modifier.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Icon(ic, null, modifier = Modifier.size(22.dp))
                                            Text(l, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }
                        }
                    }
                }
            },
            floatingActionButton = {
                if (tab == 0) FloatingActionButton(
                    onClick = { editing = null; showEditor = true },
                    shape = RoundedCornerShape(22.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) { Icon(Icons.Filled.Add, "فرمان جدید") }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    0 -> HomeTab(
                        onEdit = { editing = it; showEditor = true },
                        onHistory = { filter = it.id; tab = 1 },
                        onSettings = { tab = 2 },
                        onRun = { item -> if (Repo.confirm) { toConfirm = item } else { trigger(item) } })
                    1 -> HistoryTab(filter) { filter = it }
                    else -> SettingsTab()
                }
            }
        }
    }

    if (showEditor) EditorSheet(
        initial = editing, onDismiss = { showEditor = false },
        onSave = { Repo.upsert(it); Engine.schedule(ctx, it); showEditor = false },
        onDelete = editing?.let { e -> { Engine.remove(ctx, e.id); Repo.delete(e.id); showEditor = false } })

    toConfirm?.let { item ->
        AlertDialog(
            onDismissRequest = { toConfirm = null }, shape = RoundedCornerShape(28.dp),
            title = { Text(item.name) },
            text = { Text("${fa(item.steps.size.toString())} پیامک به شماره ${Repo.phone} ارسال شود؟") },
            confirmButton = { TextButton(onClick = { toConfirm = null; trigger(item) }) { Text("ارسال") } },
            dismissButton = { TextButton(onClick = { toConfirm = null }) { Text("انصراف") } })
    }
}

@Composable
fun HomeTab(onEdit: (Item) -> Unit, onHistory: (Item) -> Unit, onSettings: () -> Unit, onRun: (Item) -> Unit) {
    val ctx = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("فضای شما", style = MaterialTheme.typography.labelLarge, color = accent)
                        Text(Repo.appTitle.ifBlank { "خانه هوشمند" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Waves, null, tint = accent, modifier = Modifier.size(25.dp)) }
                }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))) {
                    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface))
                    Column(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f), RoundedCornerShape(30.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("وضعیت دستگاه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(if (Repo.phone.isNotBlank()) "اطلاعات دستگاه آماده است" else "برای شروع، دستگاه را پیکربندی کنید", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(Modifier.size(56.dp).clip(RoundedCornerShape(20.dp)).background((if (Repo.phone.isNotBlank()) Color(0xFF22C55E) else MaterialTheme.colorScheme.error).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { Icon(if (Repo.phone.isNotBlank()) Icons.Filled.Sensors else Icons.Filled.Settings, null, tint = if (Repo.phone.isNotBlank()) Color(0xFF22C55E) else MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassMetric("فرمان‌ها", fa(Repo.items.size.toString()), Icons.Filled.Tune, Modifier.weight(1f))
                            GlassMetric("مراحل در صف", fa(Repo.pending.size.toString()), Icons.Filled.Schedule, Modifier.weight(1f))
                        }
                        Surface(onClick = onSettings, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), contentColor = MaterialTheme.colorScheme.onSurface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f))) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Settings, null, tint = accent, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) { Text(Repo.deviceName.ifBlank { "نام دستگاه تنظیم نشده" }, fontWeight = FontWeight.SemiBold); Text(if (Repo.phone.isNotBlank()) fa(Repo.phone) else "افزودن شماره سیم‌کارت", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                                Icon(Icons.Filled.ChevronLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Repo.status?.let { msg -> Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), contentColor = MaterialTheme.colorScheme.onSurface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(msg, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); IconButton(onClick = { Repo.status = null }) { Icon(Icons.Filled.Close, "بستن") } } } }
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("فرمان‌های شما", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("برای اجرا، یک فرمان را انتخاب کنید", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Surface(shape = CircleShape, color = accent.copy(alpha = 0.14f), contentColor = accent) { Text(fa(Repo.items.size.toString()), Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge) } }
            }
        }
        if (Repo.items.isEmpty()) item { Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), contentColor = MaterialTheme.colorScheme.onSurface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Filled.AddCircleOutline, null, tint = accent, modifier = Modifier.size(42.dp)); Text("هنوز فرمانی نساخته‌اید", fontWeight = FontWeight.Bold); Text("از دکمهٔ + برای ساخت اولین فرمان استفاده کنید", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        items(Repo.items, key = { it.id }) { item -> ItemCard(item, onRun = { onRun(item) }, onEdit = { onEdit(item) }, onHistory = { onHistory(item) }, onCancel = { Engine.cancelRun(ctx, item.id) }) }
    }
}

@Composable
private fun GlassMetric(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), contentColor = MaterialTheme.colorScheme.onSurface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f))) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)); Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) } }
    }
}

@Composable
fun ItemCard(item: Item, onRun: () -> Unit, onEdit: () -> Unit, onHistory: () -> Unit, onCancel: () -> Unit) {
    val c = Color(Palette[item.color % Palette.size]); val p = Repo.pending[item.id]
    val running = Repo.activeItemId == item.id
    val busy = Repo.activeItemId != null
    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), contentColor = MaterialTheme.colorScheme.onSurface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) { Icon(IconMap[item.icon] ?: Icons.Filled.Bolt, null, tint = c, modifier = Modifier.size(28.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(if (running && p?.waitingReply == true) "در انتظار پاسخ دستگاه…" else if (p != null) "مرحله " + fa((p.idx + 1).toString()) + " · " + fmt(p.at, "HH:mm:ss") else item.schedule.label() ?: (fa(item.steps.size.toString()) + " مرحله"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Icon(if (running) Icons.Filled.Timer else Icons.Filled.Schedule, null, tint = if (running) c else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp)); Text(if (running) "در حال اجرا" else if (p != null) "در صف اجرا" else "آماده اجرا", style = MaterialTheme.typography.labelSmall, color = if (p != null) c else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(onClick = onRun, enabled = !busy, shape = CircleShape, color = if (busy && !running) MaterialTheme.colorScheme.surfaceVariant else c, contentColor = if (busy && !running) MaterialTheme.colorScheme.onSurfaceVariant else Color.White, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (running) CircularProgressIndicator(modifier = Modifier.size(23.dp), strokeWidth = 2.dp, color = Color.White)
                        else Icon(Icons.Filled.PlayArrow, "اجرا", modifier = Modifier.size(25.dp))
                    }
                }
                Row { IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) { Icon(Icons.Filled.Edit, "ویرایش", modifier = Modifier.size(16.dp)) }; IconButton(onClick = onHistory, modifier = Modifier.size(30.dp)) { Icon(Icons.Filled.History, "تاریخچه", modifier = Modifier.size(16.dp)) } }
                if (p != null) Icon(Icons.Filled.Close, "لغو", Modifier.size(18.dp).clip(CircleShape).clickable(onClick = onCancel), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}