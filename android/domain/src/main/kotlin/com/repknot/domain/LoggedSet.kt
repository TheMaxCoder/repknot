package com.repknot.domain

import kotlinx.coroutines.flow.Flow

enum class LoadUnit { KG, LB }

data class SetValues(val load: Double, val unit: LoadUnit, val reps: Int, val rir: Int?) {
    init {
        require(load.isFinite() && load >= 0) { "Load must be a finite, non-negative number." }
        require(reps > 0) { "Reps must be greater than zero." }
        require(rir == null || rir >= 0) { "RIR must be non-negative or blank." }
    }
}

data class LoggedSet(val id: String, val sessionId: String, val values: SetValues)

interface SetRepository {
    fun observeFirstSet(sessionId: String): Flow<LoggedSet?>
    suspend fun save(id: String, sessionId: String, values: SetValues)
}
