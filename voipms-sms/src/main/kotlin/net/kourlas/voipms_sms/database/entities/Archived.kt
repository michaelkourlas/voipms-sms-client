package net.kourlas.voipms_sms.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = Archived.TABLE_NAME)
class Archived(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_DATABASE_ID) val databaseId: Long = 0,
    @ColumnInfo(name = COLUMN_DID) val did: String,
    @ColumnInfo(name = COLUMN_CONTACT) val contact: String,
    @ColumnInfo(name = COLUMN_ARCHIVED) val archived: Int) {
    companion object {
        const val TABLE_NAME = "archived"

        const val COLUMN_DATABASE_ID = "DatabaseId"
        const val COLUMN_DID = "Did"
        const val COLUMN_CONTACT = "Contact"
        const val COLUMN_ARCHIVED = "Archived"
    }
}