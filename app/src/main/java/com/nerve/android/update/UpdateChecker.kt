package com.nerve.android.update

import com.nerve.android.util.Logger

class UpdateChecker(
    private val currentVersionCode: Int,
    private val fetchPayload: suspend () -> String,
) {
    suspend fun check(): UpdateState {
        val payload = runCatching { fetchPayload() }.getOrElse { error ->
            Logger.warn("UpdateChecker", "fetch_fail", mapOf("reason" to error.message))
            return UpdateState.Unknown
        }
        val info = AppVersionInfo.parse(payload)
        if (info == null) {
            Logger.warn("UpdateChecker", "parse_fail", mapOf("len" to payload.length))
            return UpdateState.Unknown
        }
        return if (info.isNewerThan(currentVersionCode)) {
            Logger.debug(
                "UpdateChecker",
                "update_available",
                mapOf("current" to currentVersionCode, "remote" to info.versionCode),
            )
            UpdateState.Available(info)
        } else {
            Logger.debug(
                "UpdateChecker",
                "up_to_date",
                mapOf("current" to currentVersionCode, "remote" to info.versionCode),
            )
            UpdateState.UpToDate
        }
    }
}
