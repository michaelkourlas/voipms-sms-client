package net.kourlas.voipms_sms.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import net.kourlas.voipms_sms.database.entities.Deleted

@Dao
interface DeletedDao {
    @Query(
        "DELETE FROM ${Deleted.TABLE_NAME} WHERE ${Deleted.COLUMN_DID} NOT IN(:dids) AND ${Deleted.COLUMN_VOIP_ID} = :voipId")
    suspend fun delete(dids: Set<String>, voipId: Long)

    @Query("DELETE FROM ${Deleted.TABLE_NAME}")
    suspend fun deleteAll()

    @Query(
        "DELETE FROM ${Deleted.TABLE_NAME} WHERE ${Deleted.COLUMN_DID} NOT IN(:dids)")
    suspend fun deleteWithoutDids(dids: Set<String>)

    @Query(
        "SELECT * FROM ${Deleted.TABLE_NAME} WHERE ${Deleted.COLUMN_DID} = :did AND ${Deleted.COLUMN_VOIP_ID} = :voipId LIMIT 1")
    suspend fun get(did: String, voipId: Long): Deleted?

    @Insert
    suspend fun insert(deleted: Deleted): Long
}