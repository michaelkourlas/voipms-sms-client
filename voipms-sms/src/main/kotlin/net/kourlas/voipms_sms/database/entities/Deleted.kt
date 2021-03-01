package net.kourlas.voipms_sms.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = Deleted.TABLE_NAME)
class Deleted(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_DATABASE_ID) val databaseId: Long = 0,
    @ColumnInfo(name = COLUMN_VOIP_ID) val voipId: Long,
    @ColumnInfo(name = COLUMN_DID) val did: String) {
    companion object {
        const val TABLE_NAME = "deleted"

        const val COLUMN_DATABASE_ID = "DatabaseId"
        const val COLUMN_VOIP_ID = "VoipId"
        const val COLUMN_DID = "Did"
    }
}