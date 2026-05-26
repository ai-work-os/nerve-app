package com.nerve.android.morning

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nerve.android.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MorningBriefReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
                    MorningBriefScheduler.schedule(context)
                    return@launch
                }
                val brief = MorningBriefFetcher(MORNING_BRIEF_BASE_URL).fetch()
                MorningBriefStore(context).save(brief)
                MorningBriefNotifier.show(context, brief)
                Logger.debug("MorningBrief", "notification_sent", mapOf("date" to brief.date))
            } catch (e: Throwable) {
                MorningBriefStore(context).saveError(e.message ?: e.toString())
                Logger.warn("MorningBrief", "fetch_failed", mapOf("reason" to e.message))
            } finally {
                MorningBriefScheduler.schedule(context)
                pending.finish()
            }
        }
    }
}
