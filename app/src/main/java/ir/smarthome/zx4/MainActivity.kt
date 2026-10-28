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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
        enableEdgeToEdge()
        Engine.scheduleAll(this)
        setContent {
            AppTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Root() } }
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
        val w = Repo.waitMs()
        if (w > 0) { Repo.status = "فاصله بین پیامک‌ها: ${fa(((w + 999) / 1000).toString())} ثانیه دیگر صبر کنید"; return }
        Engine.start(ctx, item, "manual")
        Repo.status = "«${item.name}» شروع شد" + if (item.steps.size > 1) " (${fa(item.steps.size.toString())} مرحله)" else ""
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)))) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf("خانه" to Icons.Filled.Home, "تاریخچه" to Icons.Filled.History, "تنظیمات" to Icons.Filled.Settings)
                        .forEachIndexed { i, (l, ic) ->
                            NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, l) }, label = { Text(l) })
                        }
                }
            },
            floatingActionButton = {
                if (tab == 0) ExtendedFloatingActionButton(
                    onClick = { editing = null; showEditor = true }, shape = RoundedCornerShape(20.dp),
                    icon = { Icon(Icons.Filled.Add, null) }, text = { Text("آیتم جدید") })
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
    LazyVerticalGrid(
        columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("خانه هوشمند", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                val ok = Repo.phone.isNotBlank()
                Surface(
                    onClick = onSettings, shape = CircleShape,
                    color = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (ok) Icons.Filled.Sensors else Icons.Filled.Warning, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (ok) "دستگاه: ${fa(Repo.phone)}" else "شماره دستگاه را وارد کنید",
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
                Repo.status?.let { msg ->
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(msg, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { Repo.status = null }) { Icon(Icons.Filled.Close, "بستن") }
                        }
                    }
                }
                if (Repo.items.isEmpty()) Text("هنوز آیتمی نساخته‌اید.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(Repo.items, key = { it.id }) { item ->
            ItemCard(item, onRun = { onRun(item) }, onEdit = { onEdit(item) }, onHistory = { onHistory(item) },
                onCancel = { Engine.cancelRun(ctx, item.id) })
        }
    }
}

@Composable
fun ItemCard(item: Item, onRun: () -> Unit, onEdit: () -> Unit, onHistory: () -> Unit, onCancel: () -> Unit) {
    val c = Color(Palette[item.color % Palette.size])
    val shape = RoundedCornerShape(28.dp)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "press")
    val p = Repo.pending[item.id]
    Card(
        shape = shape, elevation = CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onRun),
    ) {
        Box(Modifier.background(Brush.linearGradient(listOf(c.copy(alpha = 0.18f), Color.Transparent)))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(50.dp).background(c.copy(alpha = 0.2f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                        Icon(IconMap[item.icon] ?: Icons.Filled.Bolt, null, Modifier.size(26.dp), tint = c)
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(38.dp).background(c, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
                    }
                }
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (p != null) Icons.Filled.Timer else Icons.Filled.Schedule, null, Modifier.size(14.dp),
                        tint = if (p != null) c else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (p != null) "مرحله ${fa((p.idx + 1).toString())} ساعت ${fmt(p.at, "HH:mm:ss")}"
                        else item.schedule.label() ?: "${fa(item.steps.size.toString())} مرحله",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (p != null) Icon(Icons.Filled.Close, "لغو", Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onCancel))
                }
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onHistory, Modifier.size(34.dp)) { Icon(Icons.Filled.History, "تاریخچه", Modifier.size(19.dp)) }
                    IconButton(onClick = onEdit, Modifier.size(34.dp)) { Icon(Icons.Filled.Edit, "ویرایش", Modifier.size(19.dp)) }
                }
            }
        }
    }
}
