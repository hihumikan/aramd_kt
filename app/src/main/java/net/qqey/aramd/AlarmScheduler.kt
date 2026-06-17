package net.qqey.aramd

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.time.ZonedDateTime

object AlarmScheduler {
    const val EXTRA_ALARM_ID = "net.qqey.aramd.extra.ALARM_ID"
    const val EXTRA_ALARM_HOUR = "net.qqey.aramd.extra.ALARM_HOUR"
    const val EXTRA_ALARM_MINUTE = "net.qqey.aramd.extra.ALARM_MINUTE"

    private const val ACTION_RING = "net.qqey.aramd.action.RING"

    fun canScheduleExactAlarms(context: Context): Boolean =
        alarmManager(context).canScheduleExactAlarms()

    fun requestExactAlarmPermission(context: Context) {
        val packageUri = Uri.parse("package:${context.packageName}")
        val requestIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = packageUri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(requestIntent)
        } catch (_: ActivityNotFoundException) {
            val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = packageUri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(appSettingsIntent)
        }
    }

    fun schedule(context: Context, alarm: Alarm): Boolean {
        if (!alarm.enabled || !canScheduleExactAlarms(context)) return false

        val triggerAtMillis = nextTriggerAtMillis(alarm.hour, alarm.minute)
        val operation = ringPendingIntent(context, alarm, PendingIntent.FLAG_UPDATE_CURRENT) ?: return false
        val showIntent = PendingIntent.getActivity(
            context,
            requestCodeFor("show-${alarm.id}"),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)

        alarmManager(context).setAlarmClock(info, operation)
        return true
    }

    fun scheduleEnabled(context: Context, alarms: List<Alarm>) {
        alarms
            .filter { it.enabled }
            .forEach { schedule(context, it) }
    }

    fun cancel(context: Context, alarmId: String) {
        val pendingIntent = ringPendingIntent(
            context = context,
            alarm = Alarm(id = alarmId, hour = 0, minute = 0, enabled = false),
            flags = PendingIntent.FLAG_NO_CREATE,
        ) ?: return

        alarmManager(context).cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun ringPendingIntent(
        context: Context,
        alarm: Alarm,
        flags: Int,
    ): PendingIntent? {
        val intent = Intent(context, RingingAlarmActivity::class.java).apply {
            action = ACTION_RING
            data = Uri.parse("aramd://alarm/${Uri.encode(alarm.id)}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_HOUR, alarm.hour)
            putExtra(EXTRA_ALARM_MINUTE, alarm.minute)
        }

        return PendingIntent.getActivity(
            context,
            requestCodeFor(alarm.id),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    private fun nextTriggerAtMillis(hour: Int, minute: Int): Long {
        val now = ZonedDateTime.now()
        var next = now
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)

        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }

        return next.toInstant().toEpochMilli()
    }

    private fun requestCodeFor(key: String): Int = key.hashCode()
}
