# Contents

* [Quick Start](#quickstart)
* [Settings](#settings)
  * [Account](#account)
  * [Notifications](#notifications)
  * [Synchronization](#synchronization)
  * [Network](#network)
  * [Database](#database)
* [Donations](#donations)
* [Contacting the Developer](#contacting_the_developer)
* [Reporting Bugs](#reporting_bugs)

# Quick Start

1. Go to the VoIP.ms [Manage DID menu](https://voip.ms/m/managedid.php) and open the configuration options for the DIDs you wish to use with the app. Ensure that SMS is enabled for each DID.

2. Go to the VoIP.ms [API Configuration menu](https://www.voip.ms/m/api.php) and:

   * enable API access for your VoIP.ms account;
   * set an API password (which is **distinct** from your account password); and
   * set the list of approved IP addresses to "0.0.0.0".

3. Go the *Settings* page in the app and set:

   * the *Email address* field to the email you use to sign into the VoIP.ms portal (**not** your SIP username);
   * the *Password* field to the API password which you set above; and
   * the *Synchronization start date* field to the date on which you started using VoIP.ms.

4. Select the *DIDs (phone numbers)* option and pick the DIDs you wish to use with the app.

5. Go to the conversations list and swipe down to synchronize with VoIP.ms servers.

# Settings

## Account

### Email address and API password

This app requires access to the VoIP.ms API in order to retrieve messages from your VoIP.ms account. To facilitate this, go to the VoIP.ms [API Configuration menu](https://www.voip.ms/m/api.php) and:
* enable API access for your VoIP.ms account;
* set an API password (which is **distinct** from your account password); and
* set the list of approved IP addresses to "0.0.0.0".

The *Email address* field should be set to the email you use to sign into the VoIP.ms portal (**not** your SIP username), while the *Password* field should be set to the API password which you set above.

### DIDs (phone numbers)

The DIDs you wish to use with the app must have the SMS function enabled. To verify this, go to the VoIP.ms [Manage DID menu](https://voip.ms/m/managedid.php) and open the configuration options for the DIDs you wish to use with the app. Ensure that SMS is enabled for each DID.

This app can send and receive messages from multiple DIDs (phone numbers) in your account at a time. To configure which of your DIDs are used by the app, select the *DIDs (phone numbers)* option and then select the DIDs you wish to use.

## Synchronization

You can initiate a synchronization with the VoIP.ms servers by swiping down on the conversations list. Synchronization uses up a lot of data, so you should minimize how often you do it.

It is not necessary to perform a synchronization simply to retrieve new messages. All new messages are downloaded automatically when the main conversations screen is loaded and when a push notification is received. This limited synchronization only checks for messages dated after the most recent message stored locally, so it uses less data.

In general, it is only necessary to synchronize when you want to download messages from VoIP.ms dated before the most recent message stored locally, such as when you are first setting up the app.

### Synchronization interval

You can configure the app to schedule automatic synchronizations. Smaller intervals consume more battery life.

It is not necessary to schedule automatic synchronizations simply to retrieve new messages. All new messages are downloaded automatically when the main conversations screen is loaded and when a push notification is received.

In general, it is only necessary to schedule automatic synchronizations when you cannot use push notifications, such as when you are using the F-Droid version of the app.

### Synchronization start date

When the app synchronizes with the VoIP.ms servers, it only retrieves messages on or after the synchronization start date.

By default, this date is set to the date you installed the app. It is recommended that you set this to the date on which you created your VoIP.ms account and then perform a synchronization, so that all of your messages will be downloaded.

### Retrieve only recent messages

Normally, synchronization retrieves all messages dated on or after the synchronization start date. You can configure synchronization to retrieve only those messages from the VoIP.ms servers that are dated after the date of the most recent message that is stored locally by enabling this option.

This option significantly reduces the amount of data that synchronization uses.

### Retrieve deleted messages

Normally, synchronization does not retrieve messages that have been deleted locally. You can choose to restore these messages during synchronization by enabling this option.

## Notifications

### Push notifications

This application supports push notifications using Firebase Cloud Messaging. Push notifications are automatically setup when selecting DIDs if notifications are enabled.

However, if setup fails for whatever reason, you can configure them manually by:
* accessing the settings for your DIDs on the VoIP.ms [Manage DID menu](https://voip.ms/m/managedid.php);
* enabling the *SMS URL Callback* option; and
* entering the following URL into the neighbouring field: [https://us-central1-voip-ms-sms-9ee2b.cloudfunctions.net/notify?did={TO}](https://us-central1-voip-ms-sms-9ee2b.cloudfunctions.net/notify?did={TO})

If push notifications are configured correctly, VoIP.ms will send a callback to Google when your DID receives a text message. Google will then forward the callback to your device using Firebase Cloud Messaging. When the app receives the callback, it performs a synchronization with the VoIP.ms servers and retrieves the text message. 

To protect your privacy, the callback is configured to **only** include your DID. It does not include the text of individual messages.

### Regular notifications

If push notifications could not be enabled or if you are using the F-Droid version of the app, you will still receive notifications whenever a new message is received during synchronization with the VoIP.ms servers if notifications are enabled. 

This can be a reasonable substitute for push notifications if automatic synchronization is enabled, though obviously this solution consumes more battery life.

## Database

### Import database

This option imports the database from a specified file. The database must be in the standard SQLite binary database format and must have the database name and tables required by the app.

### Export database

This option exports the database to a specified file. This allows you to make backups of the database in case you delete messages from the VoIP.ms servers or simply want to avoid re-downloading them all from the servers after resetting your phone.

### Clean up database

This option allows you to remove certain data from the database that might be considered outdated or no longer useful, such as:
* metadata associated with deleted messages, which is stored to prevent the app from downloading deleted messages again; and
* messages and metadata associated with a DID you no longer use, which is stored in case you start using this DID again.

### Delete database

This option deletes the entire database.

# Donations

If you'd like, you can make a donation to the developer using [PayPal](https://paypal.me/kourlas). Note that making a donation does not unlock any additional features.

# Reporting Bugs

If you believe you've found a bug, feel free to file a report at the project's GitHub repository [here](https://github.com/michaelkourlas/voipms-sms-client/issues).

# Contacting the Developer

If you have any further questions, feel free to contact the developer at [michael@kourlas.com](mailto:michael@kourlas.com).