package net.qqey.aramd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.qqey.aramd.ui.theme.AramdTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AramdTheme(darkTheme = true, dynamicColor = false) {
                AlarmApp()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var canScheduleExact by remember { mutableStateOf(AlarmScheduler.canScheduleExactAlarms(context)) }
    var targetHour by remember { mutableIntStateOf(7) }
    var targetMinute by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var alarms by remember { mutableStateOf(AlarmStore.load(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            canScheduleExact = AlarmScheduler.canScheduleExactAlarms(context)
            delay(1_000)
        }
    }

    LaunchedEffect(canScheduleExact) {
        if (canScheduleExact) {
            AlarmScheduler.scheduleEnabled(context, alarms)
        }
    }

    val slots = remember(targetHour, targetMinute) {
        slotsAround(targetHour, targetMinute)
    }
    val hourListState = rememberLazyListState()
    val enabledCount = remember(alarms) { alarms.count { it.enabled } }

    LaunchedEffect(targetHour) {
        hourListState.animateScrollToItem((targetHour - 3).coerceAtLeast(0))
    }

    fun recenter(hour: Int, minute: Int) {
        targetHour = hour
        targetMinute = minute
        selected = emptySet()
    }

    fun quickSelect(fromOffset: Int, toOffset: Int) {
        selected = slots
            .filter { it.offset in fromOffset..toOffset }
            .map { it.key }
            .toSet()
    }

    fun commitAlarms(nextAlarms: List<Alarm>) {
        val sorted = nextAlarms.sortedBy { totalMin(it.hour, it.minute) }
        alarms = sorted
        AlarmStore.save(context, sorted)
    }

    fun addAlarms() {
        if (selected.isEmpty()) return

        val existingKeys = alarms.map { alarmKey(it.hour, it.minute) }.toSet()
        val newAlarms = slots
            .filter { it.key in selected && it.key !in existingKeys }
            .mapIndexed { index, slot ->
                Alarm(
                    id = "${slot.key}-${System.currentTimeMillis()}-$index",
                    hour = slot.hour,
                    minute = slot.minute,
                    enabled = true,
                )
            }

        commitAlarms(alarms + newAlarms)
        newAlarms.forEach { AlarmScheduler.schedule(context, it) }
        selected = emptySet()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = AlarmColors.Background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(AlarmColors.Background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                ScreenBand {
                    Header(now = now, enabledCount = enabledCount)
                }
            }
            item {
                ScreenBand {
                    DividerLine()
                }
            }
            if (!canScheduleExact) {
                item {
                    ScreenBand {
                        ExactAlarmPermissionCard(
                            onOpenSettings = {
                                AlarmScheduler.requestExactAlarmPermission(context)
                            },
                        )
                    }
                }
            }
            item {
                HourSelector(
                    targetHour = targetHour,
                    listState = hourListState,
                    onHourSelected = {
                        targetHour = it
                        targetMinute = 0
                        selected = emptySet()
                    },
                )
            }
            item {
                ScreenBand {
                    TargetTimeCard(hour = targetHour, minute = targetMinute)
                }
            }
            item {
                ScreenBand {
                    QuickSelectSection(
                        onSelectBeforeFour = { quickSelect(-15, 0) },
                        onSelectBeforeSix = { quickSelect(-25, 0) },
                        onSelectAround = { quickSelect(-15, 15) },
                        onClear = { selected = emptySet() },
                    )
                }
            }
            item {
                ScreenBand {
                    SlotGrid(
                        slots = slots,
                        selected = selected,
                        alarms = alarms,
                        onRecenter = ::recenter,
                    )
                }
            }
            item {
                ScreenBand {
                    AddAlarmsButton(
                        selectedCount = selected.size,
                        onAdd = ::addAlarms,
                    )
                }
            }
            item {
                ScreenBand {
                    AlarmListHeader(
                        count = alarms.size,
                        onClearAll = {
                            alarms.forEach { AlarmScheduler.cancel(context, it.id) }
                            commitAlarms(emptyList())
                        },
                    )
                }
            }
            if (alarms.isEmpty()) {
                item {
                    ScreenBand {
                        EmptyAlarms()
                    }
                }
            } else {
                items(
                    items = alarms,
                    key = { it.id },
                ) { alarm ->
                    ScreenBand {
                        AlarmRow(
                            alarm = alarm,
                            now = now,
                            onToggle = {
                                val updated = alarm.copy(enabled = !alarm.enabled)
                                if (updated.enabled) {
                                    AlarmScheduler.schedule(context, updated)
                                } else {
                                    AlarmScheduler.cancel(context, updated.id)
                                }
                                commitAlarms(alarms.map {
                                    if (it.id == alarm.id) {
                                        updated
                                    } else {
                                        it
                                    }
                                })
                            },
                            onDelete = {
                                AlarmScheduler.cancel(context, alarm.id)
                                commitAlarms(alarms.filterNot { it.id == alarm.id })
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenBand(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 384.dp)
                .padding(horizontal = 24.dp),
            content = content,
        )
    }
}

@Composable
private fun Header(now: LocalDateTime, enabledCount: Int) {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("M月d日 E", Locale.JAPANESE)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            SectionLabel(
                text = now.format(dateFormatter),
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClockPart(text = pad(now.hour))
                ClockPart(
                    text = ":",
                    modifier = Modifier.alpha(0.4f),
                )
                ClockPart(text = pad(now.minute))
            }
        }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (enabledCount > 0) AlarmColors.Primary else AlarmColors.MutedForeground),
            )
            Text(
                text = "$enabledCount 件オン",
                color = AlarmColors.MutedForeground,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ClockPart(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = AlarmColors.Foreground,
        fontFamily = FontFamily.Monospace,
        fontSize = 48.sp,
        fontWeight = FontWeight.Light,
        lineHeight = 52.sp,
    )
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AlarmColors.Border),
    )
}

@Composable
private fun ExactAlarmPermissionCard(onOpenSettings: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(shape)
            .background(AlarmColors.Card)
            .border(1.dp, AlarmColors.NextBorder, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "正確なアラーム権限が必要です",
            color = AlarmColors.Foreground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "時刻どおりに鳴らすため、Androidの「アラームとリマインダー」設定で許可してください。",
            color = AlarmColors.MutedForeground,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Box(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AlarmColors.Primary)
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "設定を開く",
                color = AlarmColors.PrimaryForeground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HourSelector(
    targetHour: Int,
    listState: LazyListState,
    onHourSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 4.dp),
    ) {
        ScreenBand {
            SectionLabel(
                text = "時刻を選択",
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier.widthIn(max = 384.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items((0..23).toList()) { hour ->
                    HourButton(
                        hour = hour,
                        selected = targetHour == hour,
                        onClick = { onHourSelected(hour) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HourButton(
    hour: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(if (selected) AlarmColors.Primary else AlarmColors.Secondary)
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = if (selected) Color.Transparent else AlarmColors.Border,
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = pad(hour),
            color = if (selected) AlarmColors.PrimaryForeground else AlarmColors.MutedForeground,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TargetTimeCard(hour: Int, minute: Int) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 16.dp)
            .clip(shape)
            .background(AlarmColors.Card)
            .border(1.dp, AlarmColors.Border, shape)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        SectionLabel(
            text = "中心時刻",
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = "${pad(hour)}:${pad(minute)}",
            color = AlarmColors.Foreground,
            fontFamily = FontFamily.Monospace,
            fontSize = 36.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 40.sp,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickSelectSection(
    onSelectBeforeFour: () -> Unit,
    onSelectBeforeSix: () -> Unit,
    onSelectAround: () -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        SectionLabel(
            text = "まとめて選択",
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickActionButton(text = "直前 4つ", onClick = onSelectBeforeFour)
            QuickActionButton(text = "直前 6つ", onClick = onSelectBeforeSix)
            QuickActionButton(text = "±15分", onClick = onSelectAround)
            QuickActionButton(
                text = "全て解除",
                subtle = true,
                onClick = onClear,
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    text: String,
    subtle: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (subtle) Color.Transparent else AlarmColors.Secondary)
            .border(
                width = 1.dp,
                color = if (subtle) AlarmColors.Border else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (subtle) AlarmColors.MutedForeground else AlarmColors.Foreground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SlotGrid(
    slots: List<TimeSlot>,
    selected: Set<String>,
    alarms: List<Alarm>,
    onRecenter: (Int, Int) -> Unit,
) {
    Column(
        modifier = Modifier.padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        slots.chunked(5).forEach { rowSlots ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowSlots.forEach { slot ->
                    val alreadySet = alarms.any { it.hour == slot.hour && it.minute == slot.minute }
                    SlotButton(
                        slot = slot,
                        selected = slot.key in selected,
                        alreadySet = alreadySet,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (slot.offset != 0) {
                                onRecenter(slot.hour, slot.minute)
                            }
                        },
                    )
                }
                repeat(5 - rowSlots.size) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotButton(
    slot: TimeSlot,
    selected: Boolean,
    alreadySet: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isCenter = slot.offset == 0
    val shape = RoundedCornerShape(12.dp)
    val background = when {
        selected -> AlarmColors.Primary
        isCenter -> AlarmColors.Secondary
        else -> AlarmColors.Card
    }
    val textColor = when {
        selected -> AlarmColors.PrimaryForeground
        isCenter -> AlarmColors.Primary
        else -> AlarmColors.Foreground
    }

    Box(
        modifier = modifier
            .height(56.dp)
            .graphicsLayer {
                scaleX = if (isCenter) 1.05f else 1f
                scaleY = if (isCenter) 1.05f else 1f
            }
            .alpha(if (alreadySet && !isCenter) 0.45f else 1f)
            .clip(shape)
            .background(background)
            .border(
                width = if (isCenter) 1.5.dp else 1.dp,
                color = if (isCenter) AlarmColors.Primary else AlarmColors.Border,
                shape = shape,
            )
            .clickable(enabled = !isCenter, onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = slot.key,
                color = textColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 13.sp,
            )
            Text(
                text = if (isCenter) "目標" else "${if (slot.offset > 0) "+" else ""}${slot.offset}分",
                color = if (selected) AlarmColors.SelectedSubText else if (isCenter) AlarmColors.Primary else AlarmColors.MutedForeground,
                fontSize = 9.sp,
                lineHeight = 11.sp,
            )
            if (alreadySet) {
                Text(
                    text = "済",
                    color = AlarmColors.MutedForeground,
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun AddAlarmsButton(
    selectedCount: Int,
    onAdd: () -> Unit,
) {
    val enabled = selectedCount > 0
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(bottom = 0.dp)
            .clip(shape)
            .background(if (enabled) AlarmColors.Primary else AlarmColors.Secondary)
            .clickable(enabled = enabled, onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (enabled) "+ $selectedCount 件を追加" else "時刻を選択してください",
            color = if (enabled) AlarmColors.PrimaryForeground else AlarmColors.MutedForeground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AlarmListHeader(
    count: Int,
    onClearAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(text = "セット済み - $count 件")
        if (count > 0) {
            Text(
                text = "全て削除",
                modifier = Modifier.clickable(onClick = onClearAll),
                color = AlarmColors.MutedForeground,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun EmptyAlarms() {
    Text(
        text = "アラームがありません",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        color = AlarmColors.MutedForeground,
        fontSize = 14.sp,
    )
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    now: LocalDateTime,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val isNextAlarm = alarm.enabled &&
        totalMin(alarm.hour, alarm.minute) > totalMin(now.hour, now.minute)
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(AlarmColors.Card)
            .border(
                width = 1.dp,
                color = if (isNextAlarm) AlarmColors.NextBorder else AlarmColors.Border,
                shape = shape,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TogglePill(
                enabled = alarm.enabled,
                onClick = onToggle,
            )
            Text(
                text = alarmKey(alarm.hour, alarm.minute),
                color = if (alarm.enabled) AlarmColors.Foreground else AlarmColors.MutedForeground,
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = if (alarm.enabled) TextDecoration.None else TextDecoration.LineThrough,
            )
            if (isNextAlarm) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(AlarmColors.PrimaryChip)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "次のアラーム",
                        color = AlarmColors.Primary,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AlarmColors.Secondary)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "削除",
                color = AlarmColors.MutedForeground,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun TogglePill(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 42.dp, height = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) AlarmColors.PrimarySoft else AlarmColors.Secondary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (enabled) "ON" else "OFF",
            color = if (enabled) AlarmColors.Primary else AlarmColors.MutedForeground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 12.sp,
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = AlarmColors.MutedForeground,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
    )
}

private data class TimeSlot(
    val hour: Int,
    val minute: Int,
    val offset: Int,
) {
    val key: String = alarmKey(hour, minute)
}

private object AlarmColors {
    val Background = Color(0xFF070C16)
    val Foreground = Color(0xFFDCE3F0)
    val Card = Color(0xFF0E1625)
    val Primary = Color(0xFFFF8F3C)
    val PrimaryForeground = Color(0xFF0A0F1A)
    val Secondary = Color(0xFF151F33)
    val MutedForeground = Color(0xFF5D7099)
    val Border = Color.White.copy(alpha = 0.07f)
    val NextBorder = Color(0x4DFF8F3C)
    val PrimarySoft = Color(0x26FF8F3C)
    val PrimaryChip = Color(0x1FFF8F3C)
    val SelectedSubText = Color(0x990A0F1A)
}

private fun slotsAround(centerHour: Int, centerMinute: Int): List<TimeSlot> =
    (-30..30 step 5).map { offset ->
        var totalMinutes = centerHour * 60 + centerMinute + offset
        if (totalMinutes < 0) totalMinutes += 24 * 60
        if (totalMinutes >= 24 * 60) totalMinutes -= 24 * 60

        TimeSlot(
            hour = totalMinutes / 60,
            minute = totalMinutes % 60,
            offset = offset,
        )
    }

@Preview(showBackground = true)
@Composable
fun AlarmAppPreview() {
    AramdTheme(darkTheme = true, dynamicColor = false) {
        AlarmApp()
    }
}
