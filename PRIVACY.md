# Privacy #

No data leaves your Android device through VoIP.ms SMS except in the following cases:
* Your VoIP.ms SMS credentials, DIDs, and messages are obviously sent to or retrieved from VoIP.ms.
* If you have push notifications enabled, VoIP.ms SMS uses Firebase Messaging and Cloud Functions to forward VoIP.ms URL callbacks to your device. This callback is configured to only contain your DID and no other information, such as the text of your messages. (This does not apply to the F-Droid version of the app.)
* VoIP.ms SMS collects some non-personally identifiable information through Firebase Analytics and Crash Reporting which is used for troubleshooting and to improve the app. (This does not apply to the F-Droid version of the app.)
