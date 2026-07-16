package ru.xvmblitz.android.capture

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object CaptureEvents {
    sealed interface Result {
        data object Loading : Result
        data object Success : Result
        data class Error(val message: String) : Result
    }

    private val _events = MutableSharedFlow<Result>(extraBufferCapacity = 8)
    val events: SharedFlow<Result> = _events.asSharedFlow()

    suspend fun emit(result: Result) {
        _events.emit(result)
    }
}
