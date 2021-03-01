package net.kourlas.voipms_sms.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import net.kourlas.voipms_sms.sms.Message

@Entity(tableName = Draft.TABLE_NAME)
class Draft(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_DATABASE_ID) val databaseId: Long = 0,
    @ColumnInfo(name = COLUMN_DID) val did: String,
    @ColumnInfo(name = COLUMN_CONTACT) val contact: String,
    @ColumnInfo(name = COLUMN_MESSAGE) val text: String) {
    fun toMessage(): Message = Message(this)

    companion object {
        const val TABLE_NAME = "draft"

        const val COLUMN_DATABASE_ID = "DatabaseId"
        const val COLUMN_DID = "Did"
        const val COLUMN_CONTACT = "Contact"
        const val COLUMN_MESSAGE = "Text"
    }
}