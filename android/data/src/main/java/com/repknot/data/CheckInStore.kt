package com.repknot.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body check-in. Server rows are a cache; `pending` rows were added on this phone and are not
 * uploaded yet (upload follows the same setting as workouts). Check-ins are add-only on the phone.
 */
@Entity(tableName = "checkins")
data class CheckInRow(
    @PrimaryKey val id: String,
    val checkedOn: String,
    val weightKg: Double? = null,
    val waistCm: Double? = null,
    val chestCm: Double? = null,
    val armsCm: Double? = null,
    val armsFlexedCm: Double? = null,
    val legsCm: Double? = null,
    val notes: String? = null,
    val pending: Boolean = false,
)

@Serializable
data class ServerCheckIn(
    val id: String,
    @SerialName("checked_on") val checkedOn: String,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("waist_cm") val waistCm: Double? = null,
    @SerialName("chest_cm") val chestCm: Double? = null,
    @SerialName("arms_cm") val armsCm: Double? = null,
    @SerialName("arms_flexed_cm") val armsFlexedCm: Double? = null,
    @SerialName("legs_cm") val legsCm: Double? = null,
    val notes: String? = null,
    val source: String? = null,
)

internal const val CHECKIN_COLUMNS = "id,checked_on,weight_kg,waist_cm,chest_cm,arms_cm,arms_flexed_cm,legs_cm,notes"

fun ServerCheckIn.toRow(): CheckInRow =
    CheckInRow(id, checkedOn, weightKg, waistCm, chestCm, armsCm, armsFlexedCm, legsCm, notes, pending = false)
fun CheckInRow.toServer(): ServerCheckIn =
    ServerCheckIn(id, checkedOn, weightKg, waistCm, chestCm, armsCm, armsFlexedCm, legsCm, notes, source = "android")

@Dao
interface CheckInDao {
    @Query("SELECT * FROM checkins ORDER BY checkedOn, id")
    fun observeAll(): Flow<List<CheckInRow>>
    @Query("SELECT * FROM checkins ORDER BY checkedOn, id")
    suspend fun all(): List<CheckInRow>
    @Query("SELECT * FROM checkins WHERE pending = 1 ORDER BY checkedOn, id")
    suspend fun pending(): List<CheckInRow>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(rows: List<CheckInRow>)
    /** A check-in added on this phone; kept until it is uploaded. */
    suspend fun add(row: CheckInRow) = put(listOf(row.copy(pending = true)))
    @Query("UPDATE checkins SET pending = 0 WHERE id = :id")
    suspend fun uploaded(id: String)
    @Query("DELETE FROM checkins WHERE pending = 0")
    suspend fun deleteCached()

    /** Replaces the server cache. Pending rows stay unless the server already has them. */
    @Transaction
    suspend fun replaceCache(remote: List<ServerCheckIn>) {
        deleteCached()
        put(remote.map { it.toRow() })
    }
}
