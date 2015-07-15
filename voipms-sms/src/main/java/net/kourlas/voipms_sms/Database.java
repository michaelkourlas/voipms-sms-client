/*
 * VoIP.ms SMS
 * Copyright (C) 2015 Michael Kourlas and other contributors
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

package net.kourlas.voipms_sms;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;
import net.kourlas.voipms_sms.activities.ConversationActivity;
import net.kourlas.voipms_sms.activities.ConversationsActivity;
import net.kourlas.voipms_sms.model.Conversation;
import net.kourlas.voipms_sms.model.Message;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Provides access to the application's database, which contains the SMS message cache.
 */
public class Database {
    public static final String TAG = "Database";

    public static final String COLUMN_DATABASE_ID = "DatabaseId";
    public static final String COLUMN_VOIP_ID = "VoipId";
    public static final String COLUMN_DATE = "Date";
    public static final String COLUMN_TYPE = "Type";
    public static final String COLUMN_DID = "Did";
    public static final String COLUMN_CONTACT = "Contact";
    public static final String COLUMN_MESSAGE = "Text";
    public static final String COLUMN_UNREAD = "Unread";
    public static final String COLUMN_DELETED = "Deleted";
    public static final String COLUMN_DELIVERED = "Delivered";
    public static final String COLUMN_DELIVERY_IN_PROGRESS = "DeliveryInProgress";

    private static final String TABLE_MESSAGE = "sms";
    private static final String[] columns = {COLUMN_DATABASE_ID, COLUMN_VOIP_ID, COLUMN_DATE, COLUMN_TYPE, COLUMN_DID,
            COLUMN_CONTACT, COLUMN_MESSAGE, COLUMN_UNREAD, COLUMN_DELETED, COLUMN_DELIVERED,
            COLUMN_DELIVERY_IN_PROGRESS};

    private static Database instance = null;

    private final Context applicationContext;
    private final Preferences preferences;
    private final SQLiteDatabase database;

    /**
     * Initializes an instance of the Database class.
     *
     * @param applicationContext The application context.
     */
    private Database(Context applicationContext) {
        this.applicationContext = applicationContext;
        preferences = Preferences.getInstance(applicationContext);
        DatabaseHelper helper = new DatabaseHelper(applicationContext);
        database = helper.getWritableDatabase();
    }

    /**
     * Gets the sole instance of the Database class. Initializes the instance if it does not already exist.
     *
     * @param applicationContext The application context.
     * @return The single instance of the Database class.
     */
    public synchronized static Database getInstance(Context applicationContext) {
        if (instance == null) {
            instance = new Database(applicationContext);
        }
        return instance;
    }

    /**
     * Adds a message to the database. If a record with the message's database ID or VoIP.ms ID already exists, that
     * record is replaced. Otherwise, a new record is created.
     *
     * @param message The message to be added to the database.
     * @return The database ID of the newly added message.
     */
    public synchronized long insertMessage(Message message) {
        ContentValues values = new ContentValues();

        if (message.getDatabaseId() != null) {
            values.put(COLUMN_DATABASE_ID, message.getDatabaseId());
        }
        else if (message.getVoipId() != null) {
            Long databaseId = getDatabaseIdForVoipId(message.getDid(), message.getVoipId());
            if (databaseId != null) {
                values.put(COLUMN_DATABASE_ID, databaseId);
            }
        }
        values.put(COLUMN_VOIP_ID, message.getVoipId());
        values.put(COLUMN_DATE, message.getDateInDatabaseFormat());
        values.put(COLUMN_TYPE, message.getTypeInDatabaseFormat());
        values.put(COLUMN_DID, message.getDid());
        values.put(COLUMN_CONTACT, message.getContact());
        values.put(COLUMN_MESSAGE, message.getText());
        values.put(COLUMN_UNREAD, message.isUnreadInDatabaseFormat());
        values.put(COLUMN_DELETED, message.isDeletedInDatabaseFormat());
        values.put(COLUMN_DELIVERED, message.isDeliveredInDatabaseFormat());
        values.put(COLUMN_DELIVERY_IN_PROGRESS, message.isDeliveryInProgressInDatabaseFormat());

        if (values.getAsLong(COLUMN_DATABASE_ID) != null) {
            return database.replace(TABLE_MESSAGE, null, values);
        }
        else {
            return database.insert(TABLE_MESSAGE, null, values);
        }
    }

    /**
     * Deletes the message with the specified database ID from the database.
     *
     * @param databaseId The database ID.
     */
    public synchronized void removeMessage(long databaseId) {
        database.delete(TABLE_MESSAGE, COLUMN_DATABASE_ID + "=" + databaseId, null);
    }

    /**
     * Deletes all messages from the database.
     */
    public synchronized void deleteAllMessages() {
        database.delete(TABLE_MESSAGE, null, null);
    }

    /**
     * Gets the message with the specified database ID from the database.
     *
     * @return The message with the specified database ID.
     */
    public synchronized Message getMessageWithDatabaseId(String did, long databaseId) {
        Cursor cursor = database.query(TABLE_MESSAGE, columns, COLUMN_DID + "=" + did + " AND " + COLUMN_DATABASE_ID +
                " = " + databaseId, null, null, null, null);
        if (cursor.moveToFirst()) {
            Message message = new Message(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATABASE_ID)),
                    cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_VOIP_ID)) ? null : cursor.getLong(
                            cursor.getColumnIndex(COLUMN_VOIP_ID)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATE)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TYPE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTACT)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_UNREAD)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELETED)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELIVERED)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELIVERY_IN_PROGRESS)));
            cursor.close();
            return message;
        }
        else {
            cursor.close();
            return null;
        }
    }

    /**
     * Gets the message with the specified VoIP.ms ID from the database.
     *
     * @return The message with the specified VoIP.ms ID.
     */
    public synchronized Message getMessageWithVoipId(String did, long voipId) {
        Cursor cursor = database.query(TABLE_MESSAGE, columns, COLUMN_DID + "=" + did + " AND " + COLUMN_VOIP_ID +
                " = " + voipId, null, null, null, null);
        if (cursor.moveToFirst()) {
            Message message = new Message(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATABASE_ID)),
                    cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_VOIP_ID)) ? null : cursor.getLong(
                            cursor.getColumnIndex(COLUMN_VOIP_ID)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATE)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TYPE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTACT)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_UNREAD)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELETED)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELIVERED)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELIVERY_IN_PROGRESS)));
            cursor.close();
            return message;
        }
        else {
            cursor.close();
            return null;
        }
    }

    /**
     * Gets all of the messages in the database.
     *
     * @return All of the messages in the database.
     */
    public synchronized Message[] getMessages() {
        List<Message> messages = new ArrayList<>();

        Cursor cursor = database.query(TABLE_MESSAGE, columns, null, null, null, null, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            Message message = new Message(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATABASE_ID)),
                    cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_VOIP_ID)) ? null : cursor.getLong(
                            cursor.getColumnIndex(COLUMN_VOIP_ID)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATE)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TYPE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTACT)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_UNREAD)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELETED)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELIVERED)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELIVERY_IN_PROGRESS)));
            messages.add(message);
            cursor.moveToNext();
        }
        cursor.close();

        Collections.sort(messages);

        Message[] messageArray = new Message[messages.size()];
        return messages.toArray(messageArray);
    }

    /**
     * Gets all of the messages in the database except for deleted messages.
     *
     * @return All of the messages in the database except for deleted messages.
     */
    public synchronized Message[] getUndeletedMessages(String did) {
        List<Message> messages = new ArrayList<>();

        Cursor cursor = database.query(TABLE_MESSAGE, columns, COLUMN_DID + "=" + did + " AND " + COLUMN_DELETED +
                "=" + "0", null, null, null, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            Message message = new Message(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATABASE_ID)),
                    cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_VOIP_ID)) ? null : cursor.getLong(
                            cursor.getColumnIndex(COLUMN_VOIP_ID)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATE)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TYPE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTACT)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_UNREAD)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELETED)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELIVERED)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELIVERY_IN_PROGRESS)));
            messages.add(message);
            cursor.moveToNext();
        }
        cursor.close();

        Collections.sort(messages);

        Message[] messageArray = new Message[messages.size()];
        return messages.toArray(messageArray);
    }

    /**
     * Gets all of the deleted messages in the database.
     *
     * @return All of the deleted messages in the database.
     */
    public synchronized Message[] getDeletedMessages(String did) {
        List<Message> messages = new ArrayList<>();

        Cursor cursor = database.query(TABLE_MESSAGE, columns, COLUMN_DID + "=" + did + " AND " + COLUMN_DELETED +
                "=" + "1", null, null, null, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            Message message = new Message(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATABASE_ID)),
                    cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_VOIP_ID)) ? null : cursor.getLong(
                            cursor.getColumnIndex(COLUMN_VOIP_ID)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATE)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TYPE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTACT)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_UNREAD)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELETED)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELIVERED)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELIVERY_IN_PROGRESS)));
            messages.add(message);
            cursor.moveToNext();
        }
        cursor.close();

        Collections.sort(messages);

        Message[] messageArray = new Message[messages.size()];
        return messages.toArray(messageArray);
    }

    /**
     * Gets all of the messages associated with the specified contact phone number, except for deleted messages.
     *
     * @param contact The contact phone number.
     * @return All of the messages associated with the specified contact phone number, except for deleted messages.
     */
    public synchronized Conversation getConversation(String did, String contact) {
        List<Message> messages = new ArrayList<>();

        Cursor cursor = database.query(TABLE_MESSAGE, columns, COLUMN_CONTACT + "=" + contact + " AND " + COLUMN_DID +
                "=" + did + " AND " + COLUMN_DELETED + "=" + "0", null, null, null, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            Message message = new Message(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATABASE_ID)),
                    cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_VOIP_ID)) ? null : cursor.getLong(
                            cursor.getColumnIndex(COLUMN_VOIP_ID)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATE)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TYPE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTACT)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_UNREAD)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELETED)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELIVERED)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELIVERY_IN_PROGRESS)));
            messages.add(message);
            cursor.moveToNext();
        }
        cursor.close();

        Message[] messageArray = new Message[messages.size()];
        messages.toArray(messageArray);
        return new Conversation(messageArray);
    }

    /**
     * Gets all of the conversations in the database with the specified DID. Conversations will not include deleted
     * messages.
     *
     * @param did The DID.
     * @return All of the conversations in the database with the specified DID. Deleted messages will not be included.
     */
    public synchronized Conversation[] getConversations(String did) {
        Message[] messages = getUndeletedMessages(did);

        List<Conversation> conversations = new ArrayList<>();
        for (Message message : messages) {
            Conversation conversation = null;
            for (Conversation c : conversations) {
                if (c.getContact().equals(message.getContact())) {
                    conversation = c;
                    break;
                }
            }

            if (conversation == null) {
                conversation = new Conversation(new Message[]{message});
                conversations.add(conversation);
            }
            else {
                conversation.addSms(message);
            }
        }
        Collections.sort(conversations);

        Conversation[] conversationArray = new Conversation[conversations.size()];
        return conversations.toArray(conversationArray);
    }

    /**
     * Synchronize database with VoIP.ms. This may include any of the following, depending on synchronization settings:
     * <li> retrieving all messages from VoIP.ms, or only those messages dated after the most recent message stored
     * locally;
     * <li> retrieving messages from VoIP.ms that were deleted locally;
     * <li> deleting messages from VoIP.ms that were deleted locally; and
     * <li> deleting messages stored locally that were deleted from VoIP.ms.
     * @param forceRecent       Retrieve only recent messages (and do nothing else) if true, regardless of synchronization
     *                          settings.
     * @param showErrors        Shows error messages if true.
     * @param sourceActivity    The calling activity.
     */
    @SuppressWarnings("SimplifiableConditionalExpression")
    public synchronized void update(boolean forceRecent, boolean showErrors, Activity sourceActivity) {
        boolean retrieveOnlyRecentMessages = forceRecent ? true : preferences.getRetrieveOnlyRecentMessages();
        boolean retrieveDeletedMessages = forceRecent ? false : preferences.getRetrieveDeletedMessages();
        boolean propagateLocalDeletions = forceRecent ? false : preferences.getPropagateLocalDeletions();
        boolean propagateRemoteDeletions = forceRecent ? false : preferences.getPropagateRemoteDeletions();

        SynchronizeDatabaseTask task = new SynchronizeDatabaseTask(applicationContext, forceRecent,
                retrieveDeletedMessages, propagateRemoteDeletions, showErrors, sourceActivity);

        if (preferences.getEmail().equals("") || preferences.getPassword().equals("") ||
                preferences.getDid().equals("")) {
            // Do not show an error; this method should never be called unless the email, password and DID are set
            task.cleanup(false, forceRecent);
            return;
        }

        if (!Utils.isNetworkConnectionAvailable(applicationContext)) {
            if (showErrors) {
                Toast.makeText(applicationContext, applicationContext.getString(R.string.database_sync_error_network),
                        Toast.LENGTH_SHORT).show();
            }
            task.cleanup(false, forceRecent);
            return;
        }

        try {
            String did = preferences.getDid();
            Message[] messages = getUndeletedMessages(did);

            List<SynchronizeDatabaseTask.RequestObject> requests = new LinkedList<>();

            // Propagate local deletions if applicable
            if (propagateLocalDeletions) {
                for (Message message : getDeletedMessages(preferences.getDid())) {
                    if (message.getVoipId() != null) {
                        String url = "https://www.voip.ms/api/v1/rest.php?" +
                                "api_username=" + URLEncoder.encode(preferences.getEmail(), "UTF-8") + "&" +
                                "api_password=" + URLEncoder.encode(preferences.getPassword(), "UTF-8") + "&" +
                                "method=deleteSMS" + "&" +
                                "id=" + message.getVoipId();
                        requests.add(new SynchronizeDatabaseTask.RequestObject(url,
                                SynchronizeDatabaseTask.RequestObject.RequestType.DELETION));
                    }
                }
            }

            // Get number of days between now and the message retrieval start date or when the most recent
            // message was received, as appropriate
            Date then = (messages.length == 0 || !retrieveOnlyRecentMessages) ?
                    preferences.getStartDate() : messages[0].getDate();
            Calendar thenCalendar = Calendar.getInstance();
            thenCalendar.setTime(then);
            thenCalendar.set(Calendar.HOUR_OF_DAY, 0);
            thenCalendar.set(Calendar.MINUTE, 0);
            thenCalendar.set(Calendar.SECOND, 0);
            thenCalendar.set(Calendar.MILLISECOND, 0);
            then = thenCalendar.getTime();

            Date now = new Date();
            Calendar nowCalendar = Calendar.getInstance();
            nowCalendar.setTime(now);
            nowCalendar.set(Calendar.HOUR_OF_DAY, 0);
            nowCalendar.set(Calendar.MINUTE, 0);
            nowCalendar.set(Calendar.SECOND, 0);
            nowCalendar.set(Calendar.MILLISECOND, 0);
            now = nowCalendar.getTime();

            long millisecondsDifference = now.getTime() - then.getTime();
            long daysDifference = (long) Math.ceil(millisecondsDifference / (1000f * 60f * 60f * 24f));

            // Split this number into 90 day periods (approximately the maximum supported by the VoIP.ms API)
            int periods = (int) Math.ceil(daysDifference / 90f);
            if (periods == 0) {
                periods = 1;
            }
            Date[] dates = new Date[periods + 1];
            dates[0] = then;
            for (int i = 1; i < dates.length - 1; i++) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(dates[i - 1]);
                calendar.add(Calendar.DAY_OF_YEAR, 90);
                dates[i] = calendar.getTime();
            }
            dates[dates.length - 1] = now;

            // Create VoIP.ms API urls for each of these periods
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            for (int i = 0; i < dates.length - 1; i++) {
                String url = "https://www.voip.ms/api/v1/rest.php?" +
                        "api_username=" + URLEncoder.encode(preferences.getEmail(), "UTF-8") + "&" +
                        "api_password=" + URLEncoder.encode(preferences.getPassword(), "UTF-8") + "&" +
                        "method=getSMS" + "&" +
                        "did=" + URLEncoder.encode(preferences.getDid(), "UTF-8") + "&" +
                        "limit=" + URLEncoder.encode("1000000", "UTF-8") + "&" +
                        "from=" + URLEncoder.encode(sdf.format(dates[i]), "UTF-8") + "&" +
                        "to=" + URLEncoder.encode(sdf.format(dates[i + 1]), "UTF-8") + "&" +
                        "timezone=-1";
                requests.add(new SynchronizeDatabaseTask.RequestObject(url,
                        SynchronizeDatabaseTask.RequestObject.RequestType.MESSAGE_RETRIEVAL, dates[i],
                        dates[i + 1]));
            }

            task.start(requests);
        } catch (UnsupportedEncodingException ex) {
            // This should never happen since the encoding (UTF-8) is hardcoded
            throw new Error(ex);
        }
    }

    /**
     * Gets the database ID for the row in the database with the specified VoIP.ms ID.
     *
     * @param voipId The VoIP.ms ID.
     * @return The database ID.
     */
    private synchronized Long getDatabaseIdForVoipId(String did, long voipId) {
        Cursor cursor = database.query(TABLE_MESSAGE, columns, COLUMN_DID + "=" + did + " AND " + COLUMN_VOIP_ID +
                "=" + voipId, null, null, null, null);
        if (cursor.moveToFirst()) {
            return cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATABASE_ID));
        }
        cursor.close();
        return null;
    }

    /**
     * Wrapper class for handling database synchronization.
     */
    private static class SynchronizeDatabaseTask {
        private final Context applicationContext;
        private final Database database;
        private final Preferences preferences;
        private final boolean forceRecent;
        private final boolean retrieveDeletedMessages;
        private final boolean propagateRemoteDeletions;
        private final boolean showErrors;
        private final Activity sourceActivity;
        private List<RequestObject> requests;

        /**
         * Initializes a new instance of the SynchronizeDatabaseTask class.
         *
         * @param forceRecent              Retrieve only recent messages (and do nothing else) if true, regardless of
         *                                 synchronization settings. This value isn't actually used; it's merely stored
         *                                 to be used during the cleanup routine.
         * @param retrieveDeletedMessages  Retrieves messages that were deleted locally from the VoIP.ms servers if
         *                                 true.
         * @param propagateRemoteDeletions Deletes local copies of messages if they were deleted from the VoIP.ms
         *                                 servers if true.
         * @param showErrors               Shows error messages if true.
         * @param sourceActivity           The calling activity.
         */
        public SynchronizeDatabaseTask(Context applicationContext, boolean forceRecent, boolean retrieveDeletedMessages,
                                       boolean propagateRemoteDeletions, boolean showErrors, Activity sourceActivity) {
            this.applicationContext = applicationContext;
            this.database = Database.getInstance(applicationContext);
            this.preferences = Preferences.getInstance(applicationContext);

            this.requests = null;

            this.forceRecent = forceRecent;
            this.retrieveDeletedMessages = retrieveDeletedMessages;
            this.propagateRemoteDeletions = propagateRemoteDeletions;
            this.showErrors = showErrors;
            this.sourceActivity = sourceActivity;
        }

        /**
         * Starts the database update.
         *
         * @param requests The VoIP.ms API request objects to use to facilitate the database update.
         */
        public void start(List<RequestObject> requests) {
            this.requests = requests;
            start(0);
        }

        /**
         * Continues the database update.
         *
         * @param i The index of the VoIP.ms API request object to use for the next part of the update.
         */
        private void start(int i) {
            new CustomAsyncTask().execute(i);
        }

        /**
         * Cleans up after the database update.
         */
        public void cleanup(boolean success, boolean forceRecent) {
            if (sourceActivity instanceof ConversationsActivity) {
                ((ConversationsActivity) sourceActivity).postUpdate();
            }
            else if (sourceActivity instanceof ConversationActivity) {
                ((ConversationActivity) sourceActivity).postUpdate();
            }
            else if (sourceActivity == null) {
                if (ActivityMonitor.getInstance().getCurrentActivity() instanceof ConversationsActivity) {
                    ((ConversationsActivity) ActivityMonitor.getInstance().getCurrentActivity()).postUpdate();
                }
                else if (ActivityMonitor.getInstance().getCurrentActivity() instanceof ConversationActivity) {
                    ((ConversationActivity) ActivityMonitor.getInstance().getCurrentActivity()).postUpdate();
                }
            }

            if (success && !forceRecent) {
                preferences.setLastCompleteSyncTime(System.currentTimeMillis());
            }
        }

        private static class RequestObject {
            private String url;
            private RequestType requestType;
            private Date startDate;
            private Date endDate;
            public RequestObject(String url, RequestType requestType) {
                this.url = url;
                this.requestType = requestType;
                this.startDate = null;
                this.endDate = null;
            }

            public RequestObject(String url, RequestType requestType, Date startDate, Date endDate) {
                this.url = url;
                this.requestType = requestType;
                this.startDate = startDate;
                this.endDate = endDate;
            }

            public String getUrl() {
                return url;
            }

            public RequestType getRequestType() {
                return requestType;
            }

            public Date getStartDate() {
                return startDate;
            }

            public Date getEndDate() {
                return endDate;
            }

            public enum RequestType {
                MESSAGE_RETRIEVAL,
                DELETION
            }
        }

        /**
         * Custom AsyncTask for use with database updating.
         */
        private class CustomAsyncTask extends AsyncTask<Integer, Void, Boolean> {
            private RequestObject request;

            @Override
            protected Boolean doInBackground(Integer... params) {
                request = requests.get(params[0]);
                JSONObject resultJson;
                try {
                    resultJson = Utils.getJson(request.getUrl());
                } catch (JSONException ex) {
                    Log.w(TAG, Log.getStackTraceString(ex));
                    if (showErrors) {
                        Toast.makeText(applicationContext, applicationContext.getString(
                                R.string.database_sync_error_api_parse), Toast.LENGTH_SHORT).show();
                    }
                    return false;
                } catch (Exception ex) {
                    Log.w(TAG, Log.getStackTraceString(ex));
                    if (showErrors) {
                        Toast.makeText(applicationContext, applicationContext.getString(
                                R.string.database_sync_error_api_request), Toast.LENGTH_SHORT).show();
                    }
                    return false;
                }

                // Parse the VoIP.ms API response
                String status = resultJson.optString("status");
                if (status == null) {
                    if (showErrors) {
                        Toast.makeText(applicationContext, applicationContext.getString(
                                R.string.database_sync_error_api_parse), Toast.LENGTH_SHORT).show();
                    }
                    return false;
                }
                if (!status.equals("success")) {
                    if (!status.equals("no_sms")) {
                        if (showErrors) {
                            Toast.makeText(applicationContext, applicationContext.getString(
                                            R.string.database_sync_error_api_error).replace("{error}", status),
                                    Toast.LENGTH_SHORT).show();
                        }
                        return false;
                    }

                    // Continue the database update by calling the next URL; otherwise, if the database update is
                    // complete, clean up
                    int current = requests.indexOf(request);
                    if (current != requests.size() - 1) {
                        start(current + 1);
                        return null;
                    }
                    return true;
                }

                if (request.getRequestType() == RequestObject.RequestType.DELETION) {
                    // Continue the database update by calling the next URL; otherwise, if the database update is
                    // complete, clean up
                    int current = requests.indexOf(request);
                    if (current != requests.size() - 1) {
                        start(current + 1);
                        return null;
                    }
                    return true;
                }

                // Extract messages from the VoIP.ms API response
                List<Message> serverMessages = new ArrayList<>();
                JSONArray rawMessages = resultJson.optJSONArray("sms");
                if (rawMessages == null) {
                    if (showErrors) {
                        Toast.makeText(applicationContext, applicationContext.getString(
                                R.string.database_sync_error_api_parse), Toast.LENGTH_SHORT).show();
                    }
                    return false;
                }
                for (int i = 0; i < rawMessages.length(); i++) {
                    JSONObject rawSms = rawMessages.optJSONObject(i);
                    if (rawSms == null || rawSms.optString("id") == null || rawSms.optString("date") == null ||
                            rawSms.optString("type") == null || rawSms.optString("did") == null ||
                            rawSms.optString("contact") == null || rawSms.optString("message") == null) {
                        if (showErrors) {
                            Toast.makeText(applicationContext, applicationContext.getString(
                                    R.string.database_sync_error_api_parse), Toast.LENGTH_SHORT).show();
                        }
                        return false;
                    }

                    String id = rawSms.optString("id");
                    String date = rawSms.optString("date");
                    String type = rawSms.optString("type");
                    String did = rawSms.optString("did");
                    String contact = rawSms.optString("contact");
                    String message = rawSms.optString("message");
                    try {
                        Message sms = new Message(id, date, type, did, contact, message);
                        serverMessages.add(sms);
                    } catch (ParseException ex) {
                        Log.w(TAG, Log.getStackTraceString(ex));
                        if (showErrors) {
                            Toast.makeText(applicationContext, applicationContext.getString(
                                    R.string.database_sync_error_api_parse), Toast.LENGTH_SHORT).show();
                        }
                        return false;
                    }
                }

                // Add new messages from the server
                List<Message> newMessages = new ArrayList<>();
                for (Message serverMessage : serverMessages) {
                    Message localMessage = database.getMessageWithVoipId(preferences.getDid(),
                            serverMessage.getVoipId());
                    if (localMessage != null) {
                        if (localMessage.isDeleted()) {
                            if (retrieveDeletedMessages) {
                                serverMessage.setUnread(localMessage.isUnread());
                                database.insertMessage(serverMessage);
                            }
                        }
                        else {
                            serverMessage.setUnread(localMessage.isUnread());
                            database.insertMessage(serverMessage);
                        }
                    }
                    else {
                        database.insertMessage(serverMessage);
                        newMessages.add(serverMessage);
                    }
                }

                // Delete old messages stored locally, if applicable
                if (propagateRemoteDeletions) {
                    Message[] localMessages = database.getUndeletedMessages(preferences.getDid());
                    for (Message localMessage : localMessages) {
                        if (localMessage.getVoipId() == null) {
                            continue;
                        }

                        boolean match = false;
                        for (Message serverMessage : serverMessages) {
                            if (serverMessage.getVoipId() != null && localMessage.getVoipId().equals(
                                    serverMessage.getVoipId())) {
                                match = true;
                                break;
                            }
                        }

                        if (!match) {
                            Date startDate = request.getStartDate();
                            Date endDate = request.getEndDate();
                            endDate.setTime(endDate.getTime() + (1000l * 60l * 60l * 24l));

                            if ((localMessage.getDate().getTime() == startDate.getTime() ||
                                    localMessage.getDate().after(startDate)) &&
                                    (localMessage.getDate().getTime() == endDate.getTime() ||
                                            localMessage.getDate().before(endDate))) {
                                if (localMessage.getDatabaseId() != null) {
                                    database.removeMessage(localMessage.getDatabaseId());
                                }
                            }
                        }
                    }
                }

                // Show notifications for new messages
                Set<String> newContacts = new HashSet<>();
                for (Message newMessage : newMessages) {
                    newContacts.add(newMessage.getContact());
                }
                Notifications.getInstance(applicationContext).showNotifications(new LinkedList<>(newContacts));

                int current = requests.indexOf(request);
                if (current != requests.size() - 1) {
                    start(current + 1);
                    return null;
                }
                return true;
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (success != null) {
                    cleanup(success, forceRecent);
                }
            }
        }
    }

    /**
     * Subclass of the SQLiteOpenHelper class for use with the Database class.
     */
    private class DatabaseHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "sms.db";
        private static final int DATABASE_VERSION = 6;
        private static final String DATABASE_CREATE = "CREATE TABLE " + TABLE_MESSAGE + "(" +
                COLUMN_DATABASE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                COLUMN_VOIP_ID + " INTEGER," +
                COLUMN_DATE + " INTEGER NOT NULL," +
                COLUMN_TYPE + " INTEGER NOT NULL," +
                COLUMN_DID + " TEXT NOT NULL," +
                COLUMN_CONTACT + " TEXT NOT NULL, " +
                COLUMN_MESSAGE + " TEXT NOT NULL," +
                COLUMN_UNREAD + " INTEGER NOT NULL," +
                COLUMN_DELETED + " INTEGER NOT NULL," +
                COLUMN_DELIVERED + " INTEGER NOT NULL," +
                COLUMN_DELIVERY_IN_PROGRESS + " INTEGER NOT NULL)";

        /**
         * Initializes a new instance of the DatabaseHelper class.
         *
         * @param context The context to be used by SQLiteOpenHelper.
         */
        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        /**
         * Creates the messages table within an SQLite database.
         *
         * @param db The SQLite database.
         */
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(DATABASE_CREATE);
        }

        /**
         * Upgrades the messages table within an SQLite database upon a version change.
         *
         * @param db         The SQLite database.
         * @param oldVersion The old version of the database.
         * @param newVersion The new version of the database.
         */
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGE);
            onCreate(db);
        }
    }
}
