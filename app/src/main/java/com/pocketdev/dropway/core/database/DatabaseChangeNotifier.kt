package com.pocketdev.dropway.core.database

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object DatabaseChangeNotifier {
    private val _tableChanges = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val tableChanges: SharedFlow<String> = _tableChanges.asSharedFlow()

    fun notifyChanged(vararg tableNames: String) {
        for (table in tableNames) {
            _tableChanges.tryEmit(table)
        }
    }
}
