package net.qqey.aramd

data class Alarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
)
