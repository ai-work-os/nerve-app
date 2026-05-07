package com.nerve.android.update

sealed interface UpdateState {
    data object Unknown : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: AppVersionInfo) : UpdateState
}
