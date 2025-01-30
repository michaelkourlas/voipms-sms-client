# Privacy Policy

This privacy policy is in reference to:

* the app "VoIP.ms SMS", hosted on the Google Play and F-Droid app stores; and
* the developer "Michael Kourlas".

The purpose of this policy is to comprehensively disclose how the app accesses,
collects, uses, and shares user data.

## Data collected to implement core app functionality

The app collects and uses:

* your VoIP.ms credentials;
* the DIDs in your VoIP.ms account;
* SMS/MMS messages sent or received using VoIP.ms; and
* metadata associated with each such SMS/MMS message, including the ID
  assigned to each message by VoIP.ms, the date and time the message was sent,
  and the source and destination phone numbers.

The app stores this data on your device using the standard mechanisms provided
by Android for storing app data. You can delete all data stored by the app on
your device at any time by simply deleting the app.

By using the app, you direct the app to share this data with
[VoIP.ms](https://voip.ms/), which accesses, collects, uses, and shares this
data in accordance with its
own [privacy policy](https://voip.ms/privacy-policy).

The collection, usage, storage, and sharing of this data as described in this
section is essential to implement the app's core functionality.

## Data collected to implement push notifications

This section only applies to the Google Play version of the app, not the
F-Droid version of the app.

If you enable notifications, the app automatically configures your VoIP.ms
account to
ping [a web service hosted by Michael Kourlas](https://voipmssms-notify.kourlas.com)
(the "Push Notification Service") every time you receive an SMS/MMS message.

The Push Notification Service is implemented as a Cloudflare Worker with source
code [available here](https://github.com/michaelkourlas/voipms-sms-push-notifications).
The Push Notification Service also uses Firebase Cloud Messaging, a service
provided by Google.

The app registers all configured DIDs with Firebase Cloud Messaging. These DIDs
are associated with
an [installation ID](https://firebase.google.com/docs/projects/manage-installations)
that is unique to every installed instance of the app.

When you receive an SMS/MMS message, the Push Notification Service provides
your DID to Firebase Cloud Messaging, which uses it to forward the ping to your
device. This allows the app to request the new message from VoIP.ms and display
a notification for it.

The callback URL configured by the app only includes your DID and no other
information, such as the text of your SMS/MMS messages. The ping simply tells
the app to check for new messages.

The Push Notification Service does not itself store your DID for longer than
required to forward the DID to Firebase Cloud Messaging.

Firebase Cloud Messaging will maintain the registration of a DID and its
association with an installation ID indefinitely. To delete this registration
and the installation ID, collect the installation ID from the "About" section
of the app and contact Michael Kourlas for assistance.

The collection, usage, storage, and sharing of your DID as described in this
section is essential to implement the app's support for push notifications.

## Contact information

If you have any questions or concerns about this policy, please contact
Michael Kourlas at voipmssms@kourlas.com.