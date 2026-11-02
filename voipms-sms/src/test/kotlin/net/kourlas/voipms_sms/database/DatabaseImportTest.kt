/*
 * VoIP.ms SMS
 * Copyright (C) 2026 Michael Kourlas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kourlas.voipms_sms.database

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.sms.ConversationId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile

/**
 * Tests for [Database.import]. The tests use the plain Application class, so
 * that CustomApplication.onCreate does not start Firebase, WorkManager or
 * network callbacks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DatabaseImportTest {
    private lateinit var context: Context
    private lateinit var database: Database
    private val conversationId = ConversationId("5145550100", "5145550199")

    @Before
    fun setUp() {
        resetDatabaseInstance()
        context = ApplicationProvider.getApplicationContext()
        database = Database.getInstance(context)
    }

    @After
    fun tearDown() {
        resetDatabaseInstance()
    }

    @Test
    fun importValidExportReplacesMessages() = runBlocking {
        insertMessages(3)
        val exportFile = exportDatabase()
        insertMessages(2)
        assertEquals(5, messageCount())

        importFile(exportFile)

        assertEquals(3, messageCount())
        assertFalse(backupFile().exists())
    }

    @Test
    fun importNonDatabaseFileKeepsMessages() = runBlocking {
        insertMessages(3)
        val file = File(context.cacheDir, "photo.jpg")
        // A JPEG header followed by data that is not a database.
        file.writeBytes(
            byteArrayOf(
                0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()
            ) + ByteArray(8192) { (it % 251).toByte() }
        )

        val e = assertThrows(DatabaseImportException::class.java) {
            runBlocking { importFile(file) }
        }

        assertEquals(
            context.getString(
                R.string.preferences_database_import_fail_damaged
            ), e.message
        )
        assertEquals(3, messageCount())
        assertFalse(backupFile().exists())
    }

    @Test
    fun importTruncatedDatabaseKeepsMessages() = runBlocking {
        insertMessages(3)
        val exportFile = exportDatabase()
        RandomAccessFile(exportFile, "rw").use {
            it.setLength(it.length() / 2)
        }

        val e = assertThrows(DatabaseImportException::class.java) {
            runBlocking { importFile(exportFile) }
        }

        assertEquals(
            context.getString(
                R.string.preferences_database_import_fail_damaged
            ), e.message
        )
        assertEquals(3, messageCount())
        assertFalse(backupFile().exists())
    }

    @Test
    fun importUnsupportedVersionKeepsMessages() = runBlocking {
        insertMessages(3)
        val exportFile = exportDatabase()
        SQLiteDatabase.openDatabase(
            exportFile.path, null, SQLiteDatabase.OPEN_READWRITE
        ).use { it.version = 8 }

        val e = assertThrows(DatabaseImportException::class.java) {
            runBlocking { importFile(exportFile) }
        }

        assertEquals(
            context.getString(
                R.string.preferences_database_import_fail_version
            ), e.message
        )
        assertEquals(3, messageCount())
        assertFalse(backupFile().exists())
    }

    @Test
    fun importDatabaseWithOtherSchemaRestoresBackup() = runBlocking {
        insertMessages(3)
        // This file passes checkImportFile, so the failure occurs when Room
        // opens it, after the current database was replaced.
        val file = File(context.cacheDir, "other.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use {
            it.execSQL("CREATE TABLE other (id INTEGER PRIMARY KEY)")
            it.version = Database.DATABASE_VERSION
        }

        assertThrows(Exception::class.java) {
            runBlocking { importFile(file) }
        }

        assertEquals(3, messageCount())
        assertFalse(backupFile().exists())

        // The restored database accepts new messages.
        insertMessages(1)
        assertEquals(4, messageCount())
    }

    private suspend fun insertMessages(count: Int) {
        database.insertConversationMessagesDeliveryInProgress(
            conversationId, List(count) { "Message $it" }
        )
    }

    private suspend fun messageCount(): Int =
        database.getConversationMessagesUnsorted(conversationId).size

    private suspend fun exportDatabase(): File {
        val file = File(context.cacheDir, "export.db")
        ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_CREATE
                or ParcelFileDescriptor.MODE_TRUNCATE
                or ParcelFileDescriptor.MODE_WRITE_ONLY
        ).use { database.export(it) }
        return file
    }

    private suspend fun importFile(file: File) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            .use { database.import(it) }
    }

    private fun backupFile(): File =
        File("${context.getDatabasePath("sms.db").absolutePath}.backup")

    /**
     * Closes the Room database of the current Database instance and removes
     * the instance, so that each test starts with a new instance for its own
     * Application object.
     */
    private fun resetDatabaseInstance() {
        val instanceField =
            Database::class.java.getDeclaredField("instance")
        instanceField.isAccessible = true
        val instance = instanceField.get(null) as Database? ?: return
        val databaseField =
            Database::class.java.getDeclaredField("database")
        databaseField.isAccessible = true
        (databaseField.get(instance) as AbstractDatabase).close()
        instanceField.set(null, null)
    }
}
