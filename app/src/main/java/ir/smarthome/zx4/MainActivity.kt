package ir.smarthome.zx4

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.UUID

val Palette = listOf(0xFF0F766E, 0xFF2563EB, 0xFFD97706, 0xFFDC2626, 0xFF7C3AED, 0xFF059669)

val IconMap: Map<String, ImageVector> = linkedMapOf(
    "bolt" to Icons.Filled.Bolt,
    "bulb" to Icons.Filled.Lightbulb,
    "power" to Icons.Filled.PowerSettingsNew,
    "lock" to Icons.Filled.Lock,
    "unlock" to Icons.Filled.LockOpen,
    "home" to Icons.Filled.Home,
    "fan" to Icons.Filled.Air,
    "water" to Icons.Filled.WaterDrop,
    "temp" to Icons.Filled.Thermostat,
    "sensors" to Icons.Filled.Sensors,
    "bell" to Icons.Filled.Notifications,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Root()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Root(vm: MainVM = viewModel()) {
    var screen by remember { mutableStateOf("home") }
    var editing by remember { mutableStateOf<Item?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Item?>(null) }
    var toConfirm by remember { mutableStateOf<Item?>(null) }
    val ctx = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) pending?.let { vm.run(it) } else vm.status = "برای ارسال پیامک باید اجازه دسترسی بدهید"
        pending = null
    }

    fun trigger(item: Item) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            vm.run(item)
        } else {
            pending = item
            launcher.launch(Manifest.permission.SEND_SMS)
        }
    }

    BackHandler(screen != "home") { screen = "home" }

    if (screen == "settings") {
        SettingsScreen(vm) { screen = "home" }
    } else {
        HomeScreen(
            vm,
            onSettings = { screen = "settings" },
            onAdd = { editing = null; showEditor = true },
            onEdit = { editing = it; showEditor = true },
            onRun = { if (vm.confirm) toConfirm = it else trigger(it) },
        )
    }

    if (showEditor) {
        EditorSheet(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { vm.upsert(it); showEditor = false },
            onDelete = editing?.let { e -> { vm.delete(e.id); showEditor = false } },
        )
    }

    toConfirm?.let { item ->
        AlertDialog(
            onDismissRequest = { toConfirm = null },
            title = { Text(item.name) },
            text = { Text("${item.commands.size} پیامک به شماره ${vm.phone} ارسال شود؟") },
            confirmButton = { TextButton(onClick = { toConfirm = null; trigger(item) }) { Text("ارسال") } },
            dismissButton = { TextButton(onClick = { toConfirm = null }) { Text("انصراف") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainVM,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Item) -> Unit,
    onRun: (Item) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("کنترل هوشمند", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "تنظیمات") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("آیتم جدید") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (vm.phone.isBlank()) {
                Card(
                    onClick = onSettings,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("شماره سیم‌کارت دستگاه را در تنظیمات وارد کنید",
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            vm.status?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(msg, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            if (vm.sending) TextButton(onClick = { vm.cancel() }) { Text("لغو") }
                            else IconButton(onClick = { vm.status = null }) { Icon(Icons.Filled.Close, "بستن") }
                        }
                        if (vm.sending) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
            if (vm.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("هنوز آیتمی نساخته‌اید.\nبا دکمه «آیتم جدید» شروع کنید.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                items(vm.items, key = { it.id }) { item ->
                    ItemCard(item, onClick = { onRun(item) }, onLong = { onEdit(item) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemCard(item: Item, onClick: () -> Unit, onLong: () -> Unit) {
    val c = Color(Palette[item.color % Palette.size])
    val shape = RoundedCornerShape(24.dp)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth().clip(shape).combinedClickable(onClick = onClick, onLongClick = onLong),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(48.dp).background(c.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(IconMap[item.icon] ?: Icons.Filled.Bolt, null, tint = c)
            }
            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                maxLines = 2, minLines = 2)
            Text("${item.commands.size} دستور • نگه‌داشتن برای ویرایش",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSheet(initial: Item?, onDismiss: () -> Unit, onSave: (Item) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "bolt") }
    var color by remember { mutableIntStateOf(initial?.color ?: 0) }
    val cmds = remember { mutableStateListOf<String>().apply { addAll(initial?.commands ?: listOf("")) } }
    val ltr = LocalTextStyleLtr()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (initial == null) "آیتم جدید" else "ویرایش آیتم",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it }, label = { Text("نام آیتم") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())

            Text("آیکون", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(IconMap.keys.toList()) { k ->
                    val sel = k == icon
                    Box(
                        Modifier.size(46.dp).clip(CircleShape)
                            .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { icon = k },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(IconMap[k]!!, null,
                            tint = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text("رنگ", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Palette.forEachIndexed { i, p ->
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(Color(p))
                            .then(if (i == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { color = i }
                    )
                }
            }

            Text("پیامک‌ها (به ترتیب ارسال می‌شوند)", style = MaterialTheme.typography.labelLarge)
            cmds.forEachIndexed { i, c ->
                OutlinedTextField(
                    value = c, onValueChange = { cmds[i] = it }, singleLine = true,
                    label = { Text("پیامک ${i + 1}") }, textStyle = ltr,
                    trailingIcon = {
                        if (cmds.size > 1) IconButton(onClick = { cmds.removeAt(i) }) { Icon(Icons.Filled.Close, "حذف") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextButton(onClick = { cmds.add("") }) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("افزودن پیامک")
            }

            Text("دستورهای آماده", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Templates) { t ->
                    AssistChip(onClick = {
                        if (cmds.size == 1 && cmds[0].isBlank()) cmds[0] = t.cmd else cmds.add(t.cmd)
                        if (name.isBlank()) name = t.label
                    }, label = { Text(t.label) })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val list = cmds.map { it.trim() }.filter { it.isNotEmpty() }
                        onSave(Item(initial?.id ?: UUID.randomUUID().toString(), name.trim(), icon, color, list))
                    },
                    enabled = name.isNotBlank() && cmds.any { it.isNotBlank() },
                    modifier = Modifier.weight(1f),
                ) { Text("ذخیره") }
                if (onDelete != null) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(6.dp)); Text("حذف")
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalTextStyleLtr() = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainVM, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("تنظیمات", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingCard {
                Text("شماره سیم‌کارت دستگاه", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = vm.phone, onValueChange = { vm.phone = it; vm.savePrefs() },
                    singleLine = true, placeholder = { Text("09123456789") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    textStyle = LocalTextStyleLtr(), modifier = Modifier.fillMaxWidth(),
                )
                Text("سیم‌کارت داخل دستگاه نباید پین‌کد داشته باشد و شماره شما باید به‌عنوان مدیر در دستگاه ثبت شده باشد.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingCard {
                Text("فاصله بین پیامک‌ها: ${vm.delaySec} ثانیه", fontWeight = FontWeight.Bold)
                Slider(
                    value = vm.delaySec.toFloat(), onValueChange = { vm.delaySec = it.toInt(); vm.savePrefs() },
                    valueRange = 20f..60f,
                )
                Text("طبق راهنمای دستگاه، فاصله ارسال هر پیامک حداقل ۲۰ ثانیه است. این مقدار فقط برای آیتم‌هایی با چند پیامک اعمال می‌شود.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("تأیید قبل از ارسال", fontWeight = FontWeight.Bold)
                        Text("قبل از ارسال هر آیتم پرسیده شود",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = vm.confirm, onCheckedChange = { vm.confirm = it; vm.savePrefs() })
                }
            }
        }
    }
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
