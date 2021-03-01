package net.kourlas.voipms_sms.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.kourlas.voipms_sms.database.entities.Draft
import net.kourlas.voipms_sms.database.entities.Sms

@Dao
interface DraftDao {
    @Query("DELETE FROM ${Draft.TABLE_NAME}")
    suspend fun deleteAll()

    @Query(
        "DELETE FROM ${Draft.TABLE_NAME} WHERE ${Sms.COLUMN_DID} = :did AND ${Sms.COLUMN_CONTACT} = :contact")
    suspend fun deleteConversation(did: String, contact: String)

    @Query(
        "DELETE FROM ${Draft.TABLE_NAME} WHERE ${Draft.COLUMN_DID} NOT IN(:dids)")
    suspend fun deleteWithoutDids(dids: Set<String>)

    @Query(
        "SELECT * FROM ${Draft.TABLE_NAME} WHERE ${Sms.COLUMN_DID} = :did AND ${Sms.COLUMN_CONTACT} = :contact")
    suspend fun getConversation(did: String, contact: String): Draft?

    @Query(
        "SELECT * FROM ${Draft.TABLE_NAME} WHERE ${Draft.COLUMN_DID} IN(:dids) ORDER BY ${Draft.COLUMN_DID} DESC, ${Draft.COLUMN_CONTACT} DESC")
    suspend fun getConversations(dids: Set<String>): List<Draft>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(draft: Draft)
}