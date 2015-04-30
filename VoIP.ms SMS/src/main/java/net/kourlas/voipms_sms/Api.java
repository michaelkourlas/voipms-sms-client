/*
 * VoIP.ms SMS
 * Copyright � 2015 Michael Kourlas
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

import android.app.*;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.v4.app.NavUtils;
import android.support.v4.app.NotificationCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import net.kourlas.voipms_sms.activities.ConversationActivity;
import net.kourlas.voipms_sms.activities.ConversationsActivity;
import net.kourlas.voipms_sms.adapters.SmsDatabaseAdapter;
import net.kourlas.voipms_sms.model.Conversation;
import net.kourlas.voipms_sms.model.Sms;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Api {

    private final Context context;
    private final Preferences preferences;
    private final SmsDatabaseAdapter smsDatabaseAdapter;

    public Api(Context context) {
        this.context = context;

        this.preferences = new Preferences(context);

        this.smsDatabaseAdapter = new SmsDatabaseAdapter(context);
        this.smsDatabaseAdapter.open();
    }

    public void updateDid() {
        if (preferences.getEmail().equals("")) {
            Toast.makeText(context.getApplicationContext(), "Update DID: VoIP.ms portal email not set",
                    Toast.LENGTH_SHORT).show();
        } else if (preferences.getPassword().equals("")) {
            Toast.makeText(context.getApplicationContext(), "Update DID: VoIP.ms API password not set",
                    Toast.LENGTH_SHORT).show();
        } else if (isNetworkConnectionAvailable()) {
            try {
                String voipUrl = "https://www.voip.ms/api/v1/rest.php?" + "&" +
                        "api_username=" + URLEncoder.encode(preferences.getEmail(), "UTF-8") + "&" +
                        "api_password=" + URLEncoder.encode(preferences.getPassword(), "UTF-8") + "&" +
                        "method=getDIDsInfo";
                new UpdateDidAsyncTask().execute(voipUrl);
            } catch (UnsupportedEncodingException ex) {
                Toast.makeText(context.getApplicationContext(),
                        "Update DID: Email address or password contains invalid characters " +
                                "(UnsupportedEncodingException)", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(context.getApplicationContext(), "Update DID: Network connection unavailable",
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void updateSmses() {
        updateSmses(false);
    }

    public void updateSmses(boolean silent) {
        if (preferences.getEmail().equals("")) {
            if (!silent) {
                Toast.makeText(context.getApplicationContext(), "Update SMSes: VoIP.ms portal email not set",
                        Toast.LENGTH_SHORT).show();
            }
        } else if (preferences.getPassword().equals("")) {
            if (!silent) {
                Toast.makeText(context.getApplicationContext(), "Update SMSes: VoIP.ms API password not set",
                        Toast.LENGTH_SHORT).show();
            }
        } else if (preferences.getDid().equals("")) {
            if (!silent) {
                Toast.makeText(context.getApplicationContext(), "Update SMSes: DID not set",
                        Toast.LENGTH_SHORT).show();
            }
        } else if (isNetworkConnectionAvailable()) {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.add(Calendar.DAY_OF_YEAR, -preferences.getDaysToSync());

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            try {
                String voipUrl = "https://www.voip.ms/api/v1/rest.php?" + "&" +
                        "api_username=" + URLEncoder.encode(preferences.getEmail(), "UTF-8") + "&" +
                        "api_password=" + URLEncoder.encode(preferences.getPassword(), "UTF-8") + "&" +
                        "method=getSMS" + "&" +
                        "did=" + URLEncoder.encode(preferences.getDid(), "UTF-8") + "&" +
                        "limit=" + URLEncoder.encode("1000000", "UTF-8") + "&" +
                        "from=" + URLEncoder.encode(sdf.format(calendar.getTime()), "UTF-8") + "&" +
                        "to=" + URLEncoder.encode(sdf.format(Calendar.getInstance(
                        TimeZone.getTimeZone("UTC")).getTime()), "UTF-8") + "&" +
                        "timezone=-1";
                new UpdateSmsesAsyncTask().execute(voipUrl, Boolean.toString(silent));
                return;
            } catch (UnsupportedEncodingException ex) {
                if (!silent) {
                    Toast.makeText(context.getApplicationContext(),
                            "Update SMSes: Email address or password contains invalid characters " +
                                    "(UnsupportedEncodingException)", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            if (!silent) {
                Toast.makeText(context.getApplicationContext(), "Update SMSes: Network connection unavailable",
                        Toast.LENGTH_SHORT).show();
            }
        }

        if (context instanceof ConversationsActivity) {
            SwipeRefreshLayout swipeRefreshLayout = (SwipeRefreshLayout) ((ConversationsActivity) context).findViewById(
                    R.id.swipe_refresh_layout);
            swipeRefreshLayout.setRefreshing(false);
        } else if (context instanceof ConversationActivity) {
            ProgressBar progressBar = (ProgressBar) ((ConversationActivity) context).findViewById(R.id.progress_bar);
            progressBar.setVisibility(View.INVISIBLE);


        }
    }

    public void deleteSms(long smsId) {
        if (preferences.getEmail().equals("")) {
            Toast.makeText(context.getApplicationContext(), "Delete SMS: VoIP.ms portal email not set",
                    Toast.LENGTH_SHORT).show();
        } else if (preferences.getPassword().equals("")) {
            Toast.makeText(context.getApplicationContext(), "Delete SMS: VoIP.ms API password not set",
                    Toast.LENGTH_SHORT).show();
        } else if (isNetworkConnectionAvailable()) {
            try {
                String voipUrl = "https://www.voip.ms/api/v1/rest.php?" + "&" +
                        "api_username=" + URLEncoder.encode(preferences.getEmail(), "UTF-8") + "&" +
                        "api_password=" + URLEncoder.encode(preferences.getPassword(), "UTF-8") + "&" +
                        "method=deleteSMS" + "&" +
                        "id=" + smsId;
                new DeleteSmsAsyncTask().execute(voipUrl, smsId + "");
            } catch (UnsupportedEncodingException ex) {
                Toast.makeText(context.getApplicationContext(),
                        "Delete SMS: Email address or password contains invalid characters " +
                                "(UnsupportedEncodingException)", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(context.getApplicationContext(), "Delete SMS: Network connection unavailable",
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void sendSms(String contact, String message) {
        if (preferences.getEmail().equals("")) {
            Toast.makeText(context.getApplicationContext(), "Send SMS: VoIP.ms portal email not set",
                    Toast.LENGTH_SHORT).show();
        } else if (preferences.getPassword().equals("")) {
            Toast.makeText(context.getApplicationContext(), "Send SMS: VoIP.ms API password not set",
                    Toast.LENGTH_SHORT).show();
        } else if (preferences.getDid().equals("")) {
            Toast.makeText(context.getApplicationContext(), "Send SMS: DID not set", Toast.LENGTH_SHORT).show();
        } else if (isNetworkConnectionAvailable()) {
            try {
                String voipUrl = "https://www.voip.ms/api/v1/rest.php?" + "&" +
                        "api_username=" + URLEncoder.encode(preferences.getEmail(), "UTF-8") + "&" +
                        "api_password=" + URLEncoder.encode(preferences.getPassword(), "UTF-8") + "&" +
                        "method=sendSMS" + "&" +
                        "did=" + URLEncoder.encode(preferences.getDid(), "UTF-8") + "&" +
                        "dst=" + URLEncoder.encode(contact, "UTF-8") + "&" +
                        "message=" + URLEncoder.encode(message, "UTF-8");
                new SendSmsAsyncTask().execute(voipUrl);
                return;
            } catch (UnsupportedEncodingException ex) {
                Toast.makeText(context.getApplicationContext(),
                        "Send SMS: Email address or password contains invalid characters " +
                                "(UnsupportedOperationException)", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(context.getApplicationContext(), "Send SMS: Network connection unavailable",
                    Toast.LENGTH_SHORT).show();
        }

        if (context instanceof ConversationActivity) {
            ProgressBar progressBar = (ProgressBar) ((ConversationActivity) context).findViewById(R.id.progress_bar);
            progressBar.setVisibility(View.INVISIBLE);
        }
    }

    private JSONObject getJson(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(10000);
            connection.setConnectTimeout(15000);
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            connection.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder data = new StringBuilder();
            String newLine = System.getProperty("line.separator");
            String line;
            while ((line = reader.readLine()) != null) {
                data.append(line);
                data.append(newLine);
            }
            reader.close();

            return new JSONObject(data.toString());
        } catch (MalformedURLException ex) {
            return null;
        } catch (IOException ex) {
            return null;
        } catch (JSONException ex) {
            return null;
        }
    }

    /**
     * Tests whether or not there is a network connection available.
     *
     * @return true if there is an available network connection, false otherwise.
     */
    private boolean isNetworkConnectionAvailable() {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private class UpdateDidAsyncTask extends AsyncTask<String, Void, JSONObject> {

        @Override
        protected JSONObject doInBackground(String... params) {
            return getJson(params[0]);
        }

        @Override
        protected void onPostExecute(JSONObject result) {
            try {
                String status = result.getString("status");
                if (status.equals("success")) {
                    final List<String> dids = new ArrayList<String>();
                    JSONArray rawDids = result.getJSONArray("dids");
                    for (int i = 0; i < rawDids.length(); i++) {
                        if (rawDids.getJSONObject(i).getString("sms_available").equals("1") &&
                                rawDids.getJSONObject(i).getString("sms_enabled").equals("1")) {
                            dids.add(rawDids.getJSONObject(i).getString("did"));
                        }
                    }

                    if (dids.size() == 0) {
                        Toast.makeText(context.getApplicationContext(), "Update DID: No DIDs in account",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        // A list is used instead of a simple integer because the variable must be made final
                        final List<Integer> selectedItemList = new ArrayList<Integer>();
                        selectedItemList.add(0);

                        final String[] didsArray = new String[dids.size()];
                        dids.toArray(didsArray);

                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle("Select DID");
                        builder.setSingleChoiceItems(didsArray, 0, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                selectedItemList.clear();
                                selectedItemList.add(which);
                            }
                        });
                        builder.setNegativeButton("Cancel", null);
                        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                int selectedItem = selectedItemList.get(0);
                                preferences.setDid(didsArray[selectedItem]);

                                smsDatabaseAdapter.deleteAllSMS();

                                if (context instanceof ConversationsActivity) {
                                    ConversationsActivity conversationsActivity = (ConversationsActivity) context;
                                    conversationsActivity.getConversationsListViewAdapter().refresh();
                                    updateSmses();
                                }
                            }
                        });
                        builder.show();
                    }
                } else {
                    Toast.makeText(context.getApplicationContext(),
                            "Update DID: VoIP.ms API returned error (" + status + ")", Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException ex) {
                Toast.makeText(context.getApplicationContext(),
                        "Update DID: Unable to parse VoIP.ms API response (JSONException)", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class UpdateSmsesAsyncTask extends AsyncTask<String, Void, List<Object>> {

        @Override
        protected List<Object> doInBackground(String... params) {
            List<Object> list = new ArrayList<Object>();
            list.add(getJson(params[0]));
            list.add(params[1]);

            return list;
        }

        @Override
        protected void onPostExecute(List<Object> list) {
            JSONObject result = (JSONObject) list.get(0);
            boolean silent = Boolean.parseBoolean((String) list.get(1));

            try {
                String status = result.getString("status");
                if (status.equals("success")) {
                    List<Sms> smses = new ArrayList<Sms>();
                    JSONArray rawSmses = result.getJSONArray("sms");
                    for (int i = 0; i < rawSmses.length(); i++) {
                        String id = rawSmses.getJSONObject(i).getString("id");
                        String date = rawSmses.getJSONObject(i).getString("date");
                        String type = rawSmses.getJSONObject(i).getString("type");
                        String did = rawSmses.getJSONObject(i).getString("did");
                        String contact = rawSmses.getJSONObject(i).getString("contact");
                        String message = rawSmses.getJSONObject(i).getString("message");

                        Sms sms = new Sms(id, date, type, did, contact, message);
                        smses.add(sms);
                    }

                    // Remove SMSes that have been deleted from the server
                    Sms[] oldSmses = smsDatabaseAdapter.getAllSmses();
                    for (Sms sms : oldSmses) {
                        if (!smses.contains(sms)) {
                            smsDatabaseAdapter.deleteSMS(sms.getId());
                        }
                    }

                    // Add new SMSes from the server
                    List<Sms> newSmses = new ArrayList<Sms>();
                    for (Sms sms : smses) {
                        if (!smsDatabaseAdapter.smsExists(sms.getId())) {
                            newSmses.add(sms);
                            smsDatabaseAdapter.addSms(sms);
                        }
                    }

                    if (silent) {
                        Conversation[] conversations = smsDatabaseAdapter.getAllConversations();
                        for (Conversation conversation : conversations) {
                            if (conversation.isUnread() && newSmses.contains(conversation.getMostRecentSms())) {

                                String smsContact = Utils.getContactName(context, conversation.getContact());
                                if (smsContact == null) {
                                    smsContact = Utils.getFormattedPhoneNumber(conversation.getContact());
                                }

                                String smsText = "";
                                long smsId = 0;
                                Sms[] smsArray = conversation.getAllSms();
                                for (Sms sms : smsArray) {
                                    if (sms.getType() == Sms.Type.INCOMING && sms.isUnread()) {
                                        smsText = smsText + "\n" + sms.getMessage();
                                        if (smsId == 0) {
                                            smsText = sms.getMessage();
                                            smsId = sms.getId();
                                        }
                                    } else {
                                        break;
                                    }
                                }

                                NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);
                                notificationBuilder.setSmallIcon(R.drawable.ic_message_white_18dp);
                                notificationBuilder.setContentTitle(smsContact);
                                notificationBuilder.setContentText(smsText);
                                notificationBuilder.setDefaults(Notification.DEFAULT_ALL);

                                Intent intent = new Intent(context, ConversationActivity.class);
                                intent.putExtra("contact", conversation.getContact());

                                TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
                                stackBuilder.addParentStack(ConversationActivity.class);
                                stackBuilder.addNextIntent(intent);
                                PendingIntent resultPendingIntent =
                                        stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

                                notificationBuilder.setContentIntent(resultPendingIntent);
                                notificationBuilder.setAutoCancel(true);

                                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                                notificationManager.notify((int) smsId, notificationBuilder.build());
                            }
                        }


                    } else if (context instanceof ConversationsActivity) {
                        ConversationsActivity conversationsActivity = (ConversationsActivity) context;
                        conversationsActivity.getConversationsListViewAdapter().refresh();

                        SwipeRefreshLayout swipeRefreshLayout = (SwipeRefreshLayout) conversationsActivity.findViewById(
                                R.id.swipe_refresh_layout);
                        swipeRefreshLayout.setRefreshing(false);
                    } else if (context instanceof ConversationActivity) {
                        ConversationActivity conversationActivity = (ConversationActivity) context;
                        conversationActivity.getConversationListViewAdapter().refresh();

                        EditText messageText = (EditText) conversationActivity.findViewById(R.id.message_edit_text);
                        messageText.setText("");

                        conversationActivity.getConversationListViewAdapter().requestScrollToBottom();

                        ProgressBar progressBar = (ProgressBar) ((ConversationActivity) context).findViewById(R.id.progress_bar);
                        progressBar.setVisibility(View.INVISIBLE);
                    }

                    return;
                } else {
                    if (!silent) {
                        Toast.makeText(context.getApplicationContext(),
                                "Update SMSes: VoIP.ms API returned error (" + status + ")", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (JSONException ex) {
                if (!silent) {
                    Toast.makeText(context.getApplicationContext(),
                            "Update SMSes: Unable to parse VoIP.ms API response (JSONException)",
                            Toast.LENGTH_SHORT).show();
                }
            } catch (ParseException ex) {
                if (!silent) {
                    Toast.makeText(context.getApplicationContext(),
                            "Update SMSes: Unable to parse VoIP.ms API response (ParseException)",
                            Toast.LENGTH_SHORT).show();
                }
            }

            if (!silent && context instanceof ConversationsActivity) {
                SwipeRefreshLayout swipeRefreshLayout = (SwipeRefreshLayout) ((ConversationsActivity) context).findViewById(
                        R.id.swipe_refresh_layout);
                swipeRefreshLayout.setRefreshing(false);
            } else if (!silent && context instanceof ConversationActivity) {
                ProgressBar progressBar = (ProgressBar) ((ConversationActivity) context).findViewById(R.id.progress_bar);
                progressBar.setVisibility(View.INVISIBLE);
            }
        }
    }

    private class DeleteSmsAsyncTask extends AsyncTask<String, Void, List<Object>> {

        @Override
        protected List<Object> doInBackground(String... params) {
            List<Object> list = new ArrayList<Object>();
            list.add(getJson(params[0]));
            list.add(params[1]);

            return list;
        }

        @Override
        protected void onPostExecute(List<Object> list) {
            try {
                JSONObject result = (JSONObject) list.get(0);
                long smsId = Long.parseLong((String) list.get(1));

                String status = result.getString("status");
                if (status.equals("success")) {
                    smsDatabaseAdapter.deleteSMS(smsId);

                    if (context instanceof ConversationsActivity) {
                        ConversationsActivity conversationsActivity = (ConversationsActivity) context;
                        conversationsActivity.getConversationsListViewAdapter().refresh();
                    } else if (context instanceof ConversationActivity) {
                        ConversationActivity conversationActivity = (ConversationActivity) context;
                        conversationActivity.getConversationListViewAdapter().refresh();

                        if (smsDatabaseAdapter.getConversation(conversationActivity.getContact()).getAllSms().length == 0) {
                            NavUtils.navigateUpFromSameTask(conversationActivity);
                        }
                    }
                } else {
                    Toast.makeText(context.getApplicationContext(),
                            "Delete SMS: VoIP.ms API returned error (" + status + ")", Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException ex) {
                Toast.makeText(context.getApplicationContext(),
                        "Delete SMS: Unable to parse VoIP.ms API response (JSONException)", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class SendSmsAsyncTask extends AsyncTask<String, Void, JSONObject> {

        @Override
        protected JSONObject doInBackground(String... params) {
            return getJson(params[0]);
        }

        @Override
        protected void onPostExecute(JSONObject result) {
            try {
                String status = result.getString("status");
                if (status.equals("success")) {
                    updateSmses();
                    return;
                } else {
                    Toast.makeText(context.getApplicationContext(),
                            "Send SMS: VoIP.ms API returned error (" + status + ")", Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException ex) {
                Toast.makeText(context.getApplicationContext(),
                        "Send SMS: Unable to parse VoIP.ms API response (JSONException)", Toast.LENGTH_SHORT).show();
            }

            if (context instanceof ConversationActivity) {
                ProgressBar progressBar = (ProgressBar) ((ConversationActivity) context).findViewById(R.id.progress_bar);
                progressBar.setVisibility(View.INVISIBLE);
            }
        }
    }
}
