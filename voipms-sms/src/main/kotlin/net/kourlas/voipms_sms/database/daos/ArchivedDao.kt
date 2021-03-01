package net.kourlas.voipms_sms.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.kourlas.voipms_sms.database.entities.Archived
import net.kourlas.voipms_sms.database.entities.Sms

@Dao
interface ArchivedDao {
    @Query("DELETE FROM ${Archived.TABLE_NAME}")
    suspend fun deleteAll()

    @Query(
        "DELETE FROM ${Archived.TABLE_NAME} WHERE ${Sms.COLUMN_DID} = :did AND ${Sms.COLUMN_CONTACT} = :contact")
    suspend fun deleteConversation(did: String, contact: String)

    @Query(
        "DELETE FROM ${Archived.TABLE_NAME} WHERE ${Archived.COLUMN_DID} NOT IN(:dids)")
    suspend fun deleteWithoutDids(dids: Set<String>)

    @Query(
        "SELECT * FROM ${Archived.TABLE_NAME} WHERE ${Sms.COLUMN_DID} = :did AND ${Sms.COLUMN_CONTACT} = :contact LIMIT 1")
    suspend fun getConversation(did: String, contact: String): Archived?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(archived: Archived)
}