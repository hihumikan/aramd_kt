package net.qqey.aramd

fun pad(number: Int) = number.toString().padStart(2, '0')

fun alarmKey(hour: Int, minute: Int) = "${pad(hour)}:${pad(minute)}"

fun totalMin(hour: Int, minute: Int) = hour * 60 + minute
