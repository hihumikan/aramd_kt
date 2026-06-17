package net.qqey.aramd

import android.content.Context

object AlarmStore {
    private const val PREFS_NAME = "aramd_alarms"
    private const val KEY_ALARMS = "alarms"
    private const val FIELD_SEPARATOR = "|"
    private const val RECORD_SEPARATOR = "\n"

    fun load(context: Context): List<Alarm> {
        val raw = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ALARMS, null)
            ?: return emptyList()

        return raw
            .lineSequence()
            .mapNotNull(::decodeAlarm)
            .sortedBy { it.hour * 60 + it.minute }
            .toList()
    }

    fun save(context: Context, alarms: List<Alarm>) {
        val encoded = alarms.joinToString(RECORD_SEPARATOR, transform = ::encodeAlarm)
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ALARMS, encoded)
            .apply()
    }

    private fun encodeAlarm(alarm: Alarm): String =
        listOf(alarm.id, alarm.hour, alarm.minute, alarm.enabled).joinToString(FIELD_SEPARATOR)

    private fun decodeAlarm(raw: String): Alarm? {
        val parts = raw.split(FIELD_SEPARATOR)
        if (parts.size != 4) return null

        val hour = parts[1].toIntOrNull() ?: return null
        val minute = parts[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null

        return Alarm(
            id = parts[0],
            hour = hour,
            minute = minute,
            enabled = parts[3].toBooleanStrictOrNull() ?: return null,
        )
    }
}
