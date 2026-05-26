package com.nerve.android.morning.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.nerve.android.morning.MORNING_BRIEF_BASE_URL
import com.nerve.android.morning.MorningBrief
import com.nerve.android.morning.MorningBriefFetcher
import com.nerve.android.morning.MorningBriefStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MorningBriefViewModel(app: Application) : AndroidViewModel(app) {
    private val store = MorningBriefStore(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var brief: MorningBrief? by mutableStateOf(store.load())
        private set
    var loading: Boolean by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(store.lastError())
        private set

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { MorningBriefFetcher(MORNING_BRIEF_BASE_URL).fetch() }
            }.onSuccess {
                store.save(it)
                brief = it
                error = null
            }.onFailure {
                error = it.message ?: it.toString()
                store.saveError(error ?: "unknown")
            }
            loading = false
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
