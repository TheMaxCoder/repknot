package com.repknot.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Uploads phone check-ins and deletes ones not uploaded yet, one at a time: a Delete waits for an
 * upload in flight, and an upload skips a row deleted meanwhile (weekly review 2026-11-18, task 040).
 * The network calls are passed in so the ordering can be tested without a server.
 */
internal class CheckInUploads(private val dao: CheckInDao) {
    private val lock = Mutex()

    /** Upserts every pending check-in; returns the first failure (the rest are still tried). */
    suspend fun push(upsert: suspend (CheckInRow) -> Unit): Exception? {
        var firstFailure: Exception? = null
        for (id in dao.pending().map { it.id }) {
            try {
                lock.withLock {
                    val row = dao.get(id)?.takeIf { it.pending } ?: return@withLock
                    upsert(row)
                    dao.uploaded(id)
                }
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (firstFailure == null) firstFailure = e }
        }
        return firstFailure
    }

    /**
     * Deletes a check-in that is not uploaded yet; returns whether it did. [remoteDelete] also
     * clears a server copy left by an upload whose acknowledgement never arrived; best effort.
     */
    suspend fun delete(id: String, remoteDelete: suspend (String) -> Unit): Boolean = lock.withLock {
        if (dao.deleteLocal(id) == 0) return@withLock false
        try { remoteDelete(id) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { }
        true
    }
}
