package com.repknot.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.repknot.domain.CoachNoteItem
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Entity(tableName = "coach_notes")
data class CoachNoteRow(@PrimaryKey val id: String, val publishedAt: String, val basedOn: String?, val headline: String, val items: String)

@Serializable
data class ServerCoachNote(val id: String, @SerialName("published_at") val publishedAt: String,
    @SerialName("based_on") val basedOn: String? = null, val headline: String, val items: JsonArray) {
    fun toRow() = CoachNoteRow(id, publishedAt, basedOn, headline, items.toString())
}

internal const val COACH_NOTE_COLUMNS = "id,published_at,based_on,headline,items"

@Dao
abstract class CoachNoteDao {
    @Query("SELECT * FROM coach_notes ORDER BY publishedAt DESC LIMIT 1")
    abstract fun observeLatest(): Flow<CoachNoteRow?>
    @Query("DELETE FROM coach_notes")
    protected abstract suspend fun deleteAll()
    @Insert
    protected abstract suspend fun insert(row: CoachNoteRow)
    @Transaction
    open suspend fun replace(latest: CoachNoteRow?) {
        deleteAll()
        if (latest != null) insert(latest)
    }
}

fun parseCoachNoteItems(json: String): List<CoachNoteItem> {
    val array = try { codec.parseToJsonElement(json) as? JsonArray } catch (_: IllegalArgumentException) { null }
    return array.orEmpty().mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        fun string(key: String): String? = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val text = string("text")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        CoachNoteItem(string("kind") ?: "focus", text, string("exercise_id"))
    }
}
