/*
 * VoIP.ms SMS
 * Copyright (C) 2015 Michael Kourlas
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.kourlas.voipms_sms;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import net.kourlas.voipms_sms.model.Conversation;
import net.kourlas.voipms_sms.model.Sms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Database {
    private static final String TABLE_SMS = "sms";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_DATE = "date";
    private static final String COLUMN_TYPE = "type";
    private static final String COLUMN_DID = "did";
    private static final String COLUMN_CONTACT = "contact";
    private static final String COLUMN_MESSAGE = "message";
    private static final String COLUMN_UNREAD = "unread";
    private static Database instance = null;
    private final String[] columns = {COLUMN_ID, COLUMN_DATE, COLUMN_TYPE, COLUMN_DID, COLUMN_CONTACT, COLUMN_MESSAGE,
            COLUMN_UNREAD};
    private SQLiteDatabase database;

    private Database(Context applicationContext) {
        SmsDatabaseHelper dbHelper = new SmsDatabaseHelper(applicationContext);
        database = dbHelper.getWritableDatabase();
    }

    public synchronized static Database getInstance(Context applicationContext) {
        if (instance == null) {
            instance = new Database(applicationContext);
        }
        return instance;
    }

    public synchronized void addSms(Sms sms) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_ID, sms.getId());
        values.put(COLUMN_DATE, sms.getRawDate());
        values.put(COLUMN_TYPE, sms.getRawType());
        values.put(COLUMN_DID, sms.getDid());
        values.put(COLUMN_CONTACT, sms.getContact());
        values.put(COLUMN_MESSAGE, sms.getMessage());
        values.put(COLUMN_UNREAD, sms.getRawUnread());
        database.insert(TABLE_SMS, null, values);
    }

    public synchronized void replaceSms(Sms sms) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_ID, sms.getId());
        values.put(COLUMN_DATE, sms.getRawDate());
        values.put(COLUMN_TYPE, sms.getRawType());
        values.put(COLUMN_DID, sms.getDid());
        values.put(COLUMN_CONTACT, sms.getContact());
        values.put(COLUMN_MESSAGE, sms.getMessage());
        values.put(COLUMN_UNREAD, sms.getRawUnread());
        database.replace(TABLE_SMS, null, values);
    }

    public synchronized void deleteSMS(long id) {
        database.delete(TABLE_SMS, COLUMN_ID + " = " + id, null);
    }

    public synchronized void deleteAllSMS() {
        database.delete(TABLE_SMS, null, null);
    }

    public synchronized boolean smsExists(long id) {
        Cursor cursor = database.query(TABLE_SMS, columns, COLUMN_ID + " = " + id,
                null, null, null, null);
        boolean exists = cursor.getCount() >= 1;
        cursor.close();
        return exists;
    }

    public synchronized Sms[] getAllSmses() {
        List<Sms> smsList = new ArrayList<Sms>();

        Cursor cursor = database.query(TABLE_SMS, columns, null, null, null, null, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            Sms sms = new Sms(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2), cursor.getString(3),
                    cursor.getString(4), cursor.getString(5), cursor.getLong(6));
            smsList.add(sms);
            cursor.moveToNext();
        }
        cursor.close();

        Sms[] smses = new Sms[smsList.size()];
        return smsList.toArray(smses);
    }

    public synchronized Conversation getConversation(String contact) {
        List<Sms> smsList = new ArrayList<Sms>();

        Cursor cursor = database.query(TABLE_SMS, columns, COLUMN_CONTACT + " = " + contact, null, null, null, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            Sms sms = new Sms(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2), cursor.getString(3),
                    cursor.getString(4), cursor.getString(5), cursor.getLong(6));
            smsList.add(sms);
            cursor.moveToNext();
        }
        cursor.close();

        Sms[] smsArray = new Sms[smsList.size()];
        smsList.toArray(smsArray);

        return new Conversation(smsArray);
    }

    public synchronized Conversation[] getAllConversations() {
        Sms[] smsArray = getAllSmses();

        List<Conversation> conversations = new ArrayList<Conversation>();
        for (Sms sms : smsArray) {
            Conversation conversation = null;
            for (Conversation c : conversations) {
                if (c.getContact().equals(sms.getContact())) {
                    conversation = c;
                    break;
                }
            }

            if (conversation == null) {
                conversation = new Conversation(new Sms[]{sms});
                conversations.add(conversation);
            } else {
                conversation.addSms(sms);
            }
        }
        Collections.sort(conversations);

        Conversation[] conversationArray = new Conversation[conversations.size()];
        return conversations.toArray(conversationArray);
    }

    private class SmsDatabaseHelper extends SQLiteOpenHelper {

        private static final String DATABASE_NAME = "sms.db";
        private static final int DATABASE_VERSION = 3;

        private static final String DATABASE_CREATE = "CREATE TABLE " + TABLE_SMS + "(" +
                COLUMN_ID + " INT PRIMARY KEY, " +
                COLUMN_DATE + " INT NOT NULL, " +
                COLUMN_TYPE + " INT NOT NULL, " +
                COLUMN_DID + " TEXT NOT NULL, " +
                COLUMN_CONTACT + " TEXT NOT NULL, " +
                COLUMN_MESSAGE + " TEXT NOT NULL, " +
                COLUMN_UNREAD + " INT NOT NULL)";

        public SmsDatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(DATABASE_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SMS);
            onCreate(db);
        }
    }
}
