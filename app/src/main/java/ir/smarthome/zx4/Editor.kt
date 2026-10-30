@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ir.smarthome.zx4

import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

data class StepUi(val cmd: String, val value: String, val minutes: Boolean, val waitForReply: Boolean = false, val timeoutValue: String = "60", val timeoutMinutes: Boolean = false)

@Composable
private fun EditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun EditorScreen(initial: Item?, onBack: () -> Unit, onSave: (Item) -> Unit, onDelete: (() -> Unit)?) {
    BackHandler(onBack = onBack)
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var icon by remember(initial?.id) { mutableStateOf(initial?.icon ?: "bolt") }
    var color by remember(initial?.id) { mutableIntStateOf(initial?.color ?: 0) }
    var showColorPicker by remember { mutableStateOf(false) }
    val steps = remember(initial?.id) {
        mutableStateListOf<StepUi>().apply {
            if (initial == null) add(StepUi("", "0", false))
            else initial.steps.forEach {
                val m = it.delaySec >= 60 && it.delaySec % 60 == 0
                val tm = it.timeoutSec >= 60 && it.timeoutSec % 60 == 0
                add(StepUi(it.cmd, (if (m) it.delaySec / 60 else it.delaySec).toString(), m,
                    it.waitForReply, (if (tm) it.timeoutSec / 60 else it.timeoutSec).toString(), tm))
            }
        }
    }
    val sc0 = initial?.schedule ?: Schedule()
    var sOn by remember(initial?.id) { mutableStateOf(sc0.enabled) }
    var everyN by remember(initial?.id) { mutableStateOf(sc0.everyN) }
    var days by remember(initial?.id) { mutableStateOf(sc0.days) }
    var nText by remember(initial?.id) { mutableStateOf(sc0.n.toString()) }
    var hour by remember(initial?.id) { mutableIntStateOf(sc0.hour) }
    var minute by remember(initial?.id) { mutableIntStateOf(sc0.minute) }
    var showTime by remember { mutableStateOf(false) }
    val ltr = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)

    fun draft(): Schedule {
        val n = (nText.toIntOrNull() ?: 1).coerceAtLeast(1)
        val anchor = if (sc0.everyN && everyN && sc0.n == n && sc0.anchorDay > 0) sc0.anchorDay else epochDay(System.currentTimeMillis())
        return Schedule(sOn, everyN, days, n, anchor, hour, minute)
    }
    fun save() {
        val valid = steps.filter { it.cmd.isNotBlank() }
        val out = valid.mapIndexed { i, s ->
            val sec = if (i == 0) 0 else ((s.value.toIntOrNull() ?: 0) * (if (s.minutes) 60 else 1)).coerceAtLeast(0)
            val timeout = (s.timeoutValue.toIntOrNull() ?: 60) * (if (s.timeoutMinutes) 60 else 1)
            Step(s.cmd.trim(), sec, s.waitForReply, timeout.coerceIn(1, 86_400))
        }
        onSave(Item(initial?.id ?: Repo.uid(), name.trim(), icon, color, out, draft()))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "افزودن آیتم" else "ویرایش آیتم", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "بازگشت") } },
                actions = { TextButton(enabled = name.isNotBlank() && steps.any { it.cmd.isNotBlank() } && !(sOn && !everyN && days.isEmpty()), onClick = ::save) { Text("ذخیره", fontWeight = FontWeight.Bold) } }
            )
        },
        bottomBar = {
            if (onDelete != null) Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Filled.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("حذف آیتم")
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                EditorSection("مشخصات آیتم") {
                    OutlinedTextField(name, { name = it }, label = { Text("نام آیتم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("آیکون", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(IconMap.keys.toList()) { k ->
                            val selected = k == icon
                            Surface(onClick = { icon = k }, shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(46.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(IconMap[k]!!, null, tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                    Text("رنگ آیتم", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        items(Palette.indices.toList()) { i ->
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Color(Palette[i]))
                                .then(if (color == i) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { color = i })
                        }
                        item {
                            Box(Modifier.size(38.dp).clip(CircleShape)
                                .background(if (color !in Palette.indices) Color(color) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable { showColorPicker = true },
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Colorize, "انتخاب رنگ دلخواه", tint = if (color !in Palette.indices) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    TextButton(onClick = { showColorPicker = true }) { Text("انتخاب رنگ دلخواه") }
                }
            }
            item { EditorSection("مراحل اجرای فرمان") { Text("پیامک‌ها به ترتیب ارسال می‌شوند و می‌توان برای هر مرحله انتظار پاسخ تعیین کرد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            itemsIndexed(steps, key = { index, _ -> "step_$index" }) { i, s ->
                EditorSection("مرحله ${fa((i + 1).toString())}") {
                    OutlinedTextField(s.cmd, { steps[i] = s.copy(cmd = it) }, singleLine = true, textStyle = ltr,
                        label = { Text("پیامک مرحله") },
                        trailingIcon = { if (steps.size > 1) IconButton(onClick = { steps.removeAt(i) }) { Icon(Icons.Filled.DeleteOutline, "حذف مرحله") } },
                        modifier = Modifier.fillMaxWidth())
                    if (i > 0) {
                        Text("تأخیر از مرحله قبل", style = MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(s.value, { steps[i] = s.copy(value = it.filter(Char::isDigit)) }, singleLine = true,
                                textStyle = ltr, modifier = Modifier.width(92.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            FilterChip(!s.minutes, { steps[i] = s.copy(minutes = false) }, { Text("ثانیه") })
                            FilterChip(s.minutes, { steps[i] = s.copy(minutes = true) }, { Text("دقیقه") })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(s.waitForReply, { steps[i] = s.copy(waitForReply = it) })
                        Column(Modifier.weight(1f)) {
                            Text("انتظار برای پاسخ دستگاه", fontWeight = FontWeight.Medium)
                            Text("تا پاسخ یا پایان مهلت، اجرای فرمان‌های دیگر متوقف می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (s.waitForReply) {
                        Text("حداکثر زمان انتظار", style = MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(s.timeoutValue, { steps[i] = s.copy(timeoutValue = it.filter(Char::isDigit)) }, singleLine = true,
                                textStyle = ltr, modifier = Modifier.width(92.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            FilterChip(!s.timeoutMinutes, { steps[i] = s.copy(timeoutMinutes = false) }, { Text("ثانیه") })
                            FilterChip(s.timeoutMinutes, { steps[i] = s.copy(timeoutMinutes = true) }, { Text("دقیقه") })
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { steps.add(StepUi("", "0", false)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("افزودن مرحله")
                }
            }
            item {
                EditorSection("دستورهای آماده") {
                    Text("برای افزودن سریع یک دستور، روی آن بزن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Templates) { t ->
                            AssistChip(onClick = {
                                if (steps.size == 1 && steps[0].cmd.isBlank()) steps[0] = steps[0].copy(cmd = t.cmd)
                                else steps.add(StepUi(t.cmd, "0", false))
                                if (name.isBlank()) name = t.label
                            }, label = { Text(t.label) })
                        }
                    }
                }
            }
            item {
                EditorSection("زمان‌بندی") {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("اجرای خودکار آیتم", Modifier.weight(1f)); Switch(sOn, { sOn = it }) }
                    if (sOn) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(!everyN, { everyN = false }, { Text("روزهای هفته") })
                            FilterChip(everyN, { everyN = true }, { Text("هر N روز") })
                        }
                        if (!everyN) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Days.forEach { (d, short, _) ->
                                val selected = d in days
                                Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { days = if (selected) days - d else days + d }, contentAlignment = Alignment.Center) {
                                    Text(short, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("هر")
                            OutlinedTextField(nText, { nText = it.filter(Char::isDigit).take(3) }, singleLine = true, textStyle = ltr,
                                modifier = Modifier.width(84.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            Text("روز یک بار", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { showTime = true }) {
                            Icon(Icons.Filled.Schedule, null); Spacer(Modifier.width(8.dp)); Text("ساعت اجرا: ${fa("%02d:%02d".format(hour, minute))}")
                        }
                        nextTrigger(draft())?.let { Text("اجرای بعدی: ${fmt(it, "EEEE y/MM/dd HH:mm")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
    }

    if (showTime) {
        val st = rememberTimePickerState(hour, minute, true)
        Dialog(onDismissRequest = { showTime = false }) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimePicker(state = st)
                    Row {
                        TextButton(onClick = { showTime = false }) { Text("انصراف") }
                        TextButton(onClick = { hour = st.hour; minute = st.minute; showTime = false }) { Text("تأیید") }
                    }
                }
            }
        }
    }
    if (showColorPicker) {
        val start = if (color in Palette.indices) Color(Palette[color]) else Color(color)
        var red by remember { mutableFloatStateOf(start.red * 255f) }
        var green by remember { mutableFloatStateOf(start.green * 255f) }
        var blue by remember { mutableFloatStateOf(start.blue * 255f) }
        val picked = Color(AndroidColor.rgb(red.toInt().coerceIn(0,255), green.toInt().coerceIn(0,255), blue.toInt().coerceIn(0,255)))
        AlertDialog(
            onDismissRequest = { showColorPicker = false }, title = { Text("انتخاب رنگ دلخواه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp)).background(picked))
                    Text("قرمز: ${red.toInt()}"); Slider(red, { red = it }, valueRange = 0f..255f)
                    Text("سبز: ${green.toInt()}"); Slider(green, { green = it }, valueRange = 0f..255f)
                    Text("آبی: ${blue.toInt()}"); Slider(blue, { blue = it }, valueRange = 0f..255f)
                }
            },
            confirmButton = { TextButton(onClick = { color = picked.toArgb(); showColorPicker = false }) { Text("انتخاب") } },
            dismissButton = { TextButton(onClick = { showColorPicker = false }) { Text("انصراف") } }
        )
    }
}
