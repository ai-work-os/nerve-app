package com.nerve.android.update

import androidx.lifecycle.ViewModel
import com.nerve.android.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UpdateViewModel(
    private val checker: UpdateChecker,
    dispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Unknown)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _dismissedVersionCode = MutableStateFlow<Int?>(null)
    val dismissedVersionCode: StateFlow<Int?> = _dismissedVersionCode.asStateFlow()

    fun refresh() {
        scope.launch {
            Logger.debug("UpdateViewModel", "refresh_begin", emptyMap())
            _state.value = checker.check()
        }
    }

    fun dismiss() {
        val current = _state.value
        if (current is UpdateState.Available) {
            _dismissedVersionCode.value = current.info.versionCode
            Logger.debug(
                "UpdateViewModel",
                "dismiss",
                mapOf("versionCode" to current.info.versionCode),
            )
        }
    }
}
