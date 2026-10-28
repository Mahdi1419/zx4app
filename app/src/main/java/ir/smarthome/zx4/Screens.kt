@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ir.smarthome.zx4

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun HistoryTab(filter: String?, onFilter: (String?) -> Unit) {
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(5000); value = System.currentTimeMillis() } }
    val list = Repo.history.filter { filter == null || it.itemId == filter }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("تاریخچه", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (Repo.history.isNotEmpty()) TextButton(onClick = { Repo.clearHistory() }) { Text("پاک کردن") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(filter == null, { onFilter(null) }, { Text("همه") }) }
            items(Repo.items, key = { it.id }) { FilterChip(filter == it.id, { onFilter(it.id) }, { Text(it.name) }) }
        }
        if (list.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("هنوز موردی ثبت نشده", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { EntryCard(it, now) }
        }
    }
}

@Composable
private fun EntryCard(e: Entry, now: Long) {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.itemName, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(fmt(e.at, "y/MM/dd  HH:mm:ss"), style = MaterialTheme.typography.bodySmall, color = sub)
            }
            if (e.cmd.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(if (e.source == "schedule") "زمان‌بندی" else "دستی", Modifier.padding(8.dp, 3.dp), style = MaterialTheme.typography.labelSmall)
                }
                Text("ارسال: ${e.cmd}", style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
            }
            e.replies.forEach { r ->
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        Text(r.text, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("پاسخ ${fmt(r.at, "HH:mm:ss")}", style = MaterialTheme.typography.labelSmall, color = sub)
                    }
                }
            }
            if (e.source != "device" && e.replies.isEmpty()) {
                val t = if (e.error != null) "خطا: ${e.error}" else if (now - e.at <= 60_000) "در انتظار پاسخ دستگاه…" else "پاسخی دریافت نشد"
                Text(t, style = MaterialTheme.typography.bodySmall, color = if (e.error != null) MaterialTheme.colorScheme.error else sub)
            }
        }
    }
}

@Composable
fun SettingsTab() {
    val ctx = LocalContext.current
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { syncDefaultSms(ctx) }
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("تنظیمات", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card2 {
            Text("شخصی‌سازی", fontWeight = FontWeight.Bold)
            OutlinedTextField(Repo.appTitle, { Repo.appTitle = it.take(30); Repo.savePrefs() }, singleLine = true,
                label = { Text("عنوان برنامه (بالای صفحه خانه)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(Repo.deviceName, { Repo.deviceName = it.take(30); Repo.savePrefs() }, singleLine = true,
                label = { Text("نام دستگاه") }, modifier = Modifier.fillMaxWidth())
            Text("تم", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("سیستم", "روشن", "تیره").forEachIndexed { i, l ->
                    FilterChip(Repo.themeMode == i, { Repo.themeMode = i; Repo.savePrefs() }, { Text(l) })
                }
            }
            Text("رنگ اصلی", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Palette.forEachIndexed { i, p ->
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(p))
                        .then(if (Repo.accent == i) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .clickable { Repo.accent = i; Repo.savePrefs() })
                }
            }
        }
        Card2 {
            Text("شماره سیم‌کارت دستگاه", fontWeight = FontWeight.Bold)
            OutlinedTextField(Repo.phone, { Repo.phone = it; Repo.savePrefs() }, singleLine = true, placeholder = { Text("09123456789") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr), modifier = Modifier.fillMaxWidth())
            Text("سیم‌کارت دستگاه نباید پین‌کد داشته باشد و شماره شما باید به‌عنوان مدیر ثبت شده باشد. پاسخ‌ها فقط از همین شماره دریافت می‌شوند.",
                style = MaterialTheme.typography.bodySmall, color = sub)
        }
        Card2 {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("تأیید قبل از ارسال", fontWeight = FontWeight.Bold)
                    Text("قبل از اجرای دستی هر آیتم پرسیده شود", style = MaterialTheme.typography.bodySmall, color = sub)
                }
                Switch(Repo.confirm, { Repo.confirm = it; Repo.savePrefs() })
            }
        }
        Card2 {
            Text("فاصله اجباری بین دستورها: ${fa(Repo.gapSec.toString())} ثانیه", fontWeight = FontWeight.Bold)
            Slider(Repo.gapSec.toFloat(), { Repo.gapSec = it.toInt().coerceIn(1, 300); Repo.savePrefs() }, valueRange = 1f..300f)
            Text("بین هر دو پیامکی که برنامه می‌فرستد، حتی از آیتم‌های مختلف، حداقل این فاصله رعایت می‌شود. اجرای هم‌زمان مهار می‌شود؛ کلیک دستی زودتر از موعد رد می‌شود و مراحل خودکار در صورت نیاز عقب می‌افتند. فاصله مراحل هر آیتم هم هیچ‌وقت کمتر از این مقدار نخواهد بود. بازه قابل تنظیم: ۱ تا ۳۰۰ ثانیه.",
                style = MaterialTheme.typography.bodySmall, color = sub)
        }
        Card2 {
            Text("حالت گوشی اختصاصی (برنامه پیش‌فرض SMS)", fontWeight = FontWeight.Bold)
            Text(if (Repo.isDefaultSms) "فعال است ✓ — پاسخ‌های ماژول فقط داخل این برنامه می‌مانند و در برنامه پیام‌ها دیده نمی‌شوند."
                else "غیرفعال — پاسخ‌های ماژول علاوه بر این برنامه، در برنامه پیام‌های گوشی هم نمایش داده می‌شوند.",
                style = MaterialTheme.typography.bodySmall, color = sub)
            Text("توجه: وقتی فعال باشد، این برنامه تمام پیامک‌های گوشی را دریافت می‌کند. پیامک‌های غیر از ماژول در صندوق سیستم ذخیره و با نوتیفیکیشن اعلام می‌شوند، اما این برنامه صفحه پیام‌رسانی ندارد؛ پس فقط برای گوشی اختصاصی مناسب است.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                if (Repo.isDefaultSms) ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                else { setSmsComponents(ctx, true); smsLauncher.launch(defaultSmsIntent(ctx)) }
            }) { Text(if (Repo.isDefaultSms) "بازگرداندن برنامه پیام‌ها (از تنظیمات سیستم)" else "فعال‌سازی") }
        }
        Card2 {
            Text("اجرای زمان‌بندی در پس‌زمینه", fontWeight = FontWeight.Bold)
            Text("برای اینکه زمان‌بندی‌ها و پیامک‌های مرحله‌ای حتی وقتی برنامه بسته است اجرا شوند، بهینه‌سازی باتری را برای این برنامه غیرفعال کنید (گزینه «بدون محدودیت»).",
                style = MaterialTheme.typography.bodySmall, color = sub)
            OutlinedButton(onClick = {
                ctx.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }) { Text("باز کردن تنظیمات باتری") }
        }
    }
}

@Composable
private fun Card2(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
