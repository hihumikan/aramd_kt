package net.qqey.aramd

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                if (AlarmScheduler.canScheduleExactAlarms(context)) {
                    AlarmScheduler.scheduleEnabled(context, AlarmStore.load(context))
                }
            }
        }
    }
}
