package com.nerve.android.morning

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nerve.android.util.Logger
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

const val MORNING_BRIEF_HOUR = 8
const val MORNING_BRIEF_MINUTE = 30
const val MORNING_BRIEF_ACTION = "com.nerve.android.morning.MORNING_BRIEF"
const val MORNING_BRIEF_BASE_URL = "http://100.75.43.90:4800"
const val MORNING_BRIEF_OPEN_EXTRA = "open_morning_brief"

fun nextMorningBriefRun(now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
    val today = now.toLocalDate().atTime(MORNING_BRIEF_HOUR, MORNING_BRIEF_MINUTE)
    return if (now.isBefore(today)) today else today.plusDays(1)
}

object MorningBriefScheduler {
    fun schedule(context: Context) {
        val next = nextMorningBriefRun()
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            Duration.ofDays(1).toMillis(),
            pendingIntent(context),
        )
        Logger.debug("MorningBrief", "scheduled", mapOf("next" to next.toString()))
    }

    fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MorningBriefReceiver::class.java).apply {
            action = MORNING_BRIEF_ACTION
        }
        return PendingIntent.getBroadcast(
            context,
            830,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
