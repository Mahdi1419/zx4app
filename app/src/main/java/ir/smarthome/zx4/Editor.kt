@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ir.smarthome.zx4

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

data class StepUi(val cmd: String, val value: String, val minutes: Boolean)

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSheet(initial: Item?, onDismiss: () -> Unit, onSave: (Item) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "bolt") }
    var color by remember { mutableIntStateOf(initial?.color ?: 0) }
    val steps = remember {
        mutableStateListOf<StepUi>().apply {
            if (initial == null) add(StepUi("", "30", false))
            else initial.steps.forEach {
                val m = it.delaySec >= 60 && it.delaySec % 60 == 0
                add(StepUi(it.cmd, (if (m) it.delaySec / 60 else it.delaySec).toString(), m))
            }
        }
    }
    val sc0 = initial?.schedule ?: Schedule()
    var sOn by remember { mutableStateOf(sc0.enabled) }
    var everyN by remember { mutableStateOf(sc0.everyN) }
    var days by remember { mutableStateOf(sc0.days) }
    var nText by remember { mutableStateOf(sc0.n.toString()) }
    var hour by remember { mutableIntStateOf(sc0.hour) }
    var minute by remember { mutableIntStateOf(sc0.minute) }
    var showTime by remember { mutableStateOf(false) }
    val ltr = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)

    fun draft(): Schedule {
        val n = (nText.toIntOrNull() ?: 1).coerceAtLeast(1)
        val anchor = if (sc0.everyN && everyN && sc0.n == n && sc0.anchorDay > 0) sc0.anchorDay else epochDay(System.currentTimeMillis())
        return Schedule(sOn, everyN, days, n, anchor, hour, minute)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (initial == null) "آیتم جدید" else "ویرایش آیتم", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Section("مشخصات") {
                OutlinedTextField(name, { name = it }, label = { Text("نام آیتم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(IconMap.keys.toList()) { k ->
                        val sel = k == icon
                        Box(Modifier.size(46.dp).clip(CircleShape)
                            .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .clickable { icon = k }, contentAlignment = Alignment.Center) {
                            Icon(IconMap[k]!!, null, tint = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Palette.forEachIndexed { i, p ->
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(p))
                            .then(if (i == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { color = i })
                    }
                }
            }

            Section("مراحل (پیامک‌ها به ترتیب ارسال می‌شوند)") {
                steps.forEachIndexed { i, s ->
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(s.cmd, { steps[i] = s.copy(cmd = it) }, singleLine = true, textStyle = ltr,
                                label = { Text("پیامک مرحله ${fa((i + 1).toString())}") },
                                trailingIcon = { if (steps.size > 1) IconButton(onClick = { steps.removeAt(i) }) { Icon(Icons.Filled.Close, "حذف") } },
                                modifier = Modifier.fillMaxWidth())
                            if (i < steps.lastIndex) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.primary)
                                Text("صبر کن")
                                OutlinedTextField(s.value, { steps[i] = s.copy(value = it.filter { c -> c.isDigit() || c == '.' }) },
                                    singleLine = true, textStyle = ltr, modifier = Modifier.width(84.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                                FilterChip(!s.minutes, { steps[i] = s.copy(minutes = false) }, { Text("ثانیه") })
                                FilterChip(s.minutes, { steps[i] = s.copy(minutes = true) }, { Text("دقیقه") })
                            }
                        }
                    }
                }
                TextButton(onClick = { steps.add(StepUi("", "30", false)) }) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("افزودن مرحله")
                }
                Text("دستورهای آماده", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Templates) { t ->
                        AssistChip(onClick = {
                            if (steps.size == 1 && steps[0].cmd.isBlank()) steps[0] = steps[0].copy(cmd = t.cmd)
                            else steps.add(StepUi(t.cmd, "30", false))
                            if (name.isBlank()) name = t.label
                        }, label = { Text(t.label) })
                    }
                }
                Text("حداقل فاصله بین دو پیامک ۲۰ ثانیه است (طبق راهنمای دستگاه).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Section("زمان‌بندی") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("اجرای خودکار آیتم", Modifier.weight(1f))
                    Switch(sOn, { sOn = it })
                }
                if (sOn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!everyN, { everyN = false }, { Text("روزهای هفته") })
                        FilterChip(everyN, { everyN = true }, { Text("هر N روز") })
                    }
                    if (!everyN) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Days.forEach { (d, short, _) ->
                            val sel = d in days
                            Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                                .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                .clickable { days = if (sel) days - d else days + d }, contentAlignment = Alignment.Center) {
                                Text(short, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("هر")
                        OutlinedTextField(nText, { nText = it.filter { c -> c.isDigit() }.take(3) }, singleLine = true, textStyle = ltr,
                            modifier = Modifier.width(84.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Text("روز یک بار (۲ = یک روز در میان) • شروع از امروز", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { showTime = true }) {
                        Icon(Icons.Filled.Schedule, null); Spacer(Modifier.width(8.dp)); Text("ساعت اجرا: ${fa("%02d:%02d".format(hour, minute))}")
                    }
                    nextTrigger(draft())?.let {
                        Text("اجرای بعدی: ${fmt(it, "EEEE y/MM/dd HH:mm")}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            val valid = steps.filter { it.cmd.isNotBlank() }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    enabled = name.isNotBlank() && valid.isNotEmpty() && !(sOn && !everyN && days.isEmpty()),
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp),
                    onClick = {
                        val out = valid.mapIndexed { i, s ->
                            val sec = if (i == valid.lastIndex) 0
                            else ((s.value.toDoubleOrNull() ?: 20.0) * (if (s.minutes) 60 else 1)).toInt().coerceAtLeast(20)
                            Step(s.cmd.trim(), sec)
                        }
                        onSave(Item(initial?.id ?: Repo.uid(), name.trim(), icon, color, out, draft()))
                    }) { Text("ذخیره") }
                if (onDelete != null) OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(6.dp)); Text("حذف")
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
}
