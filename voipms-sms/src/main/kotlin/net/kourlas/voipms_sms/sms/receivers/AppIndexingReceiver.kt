package net.kourlas.voipms_sms.sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.crashlytics.android.Crashlytics
import net.kourlas.voipms_sms.sms.services.AppIndexingService

/**
 * Broadcast receiver used to forward Firebase app indexing requests to the
 * AppIndexingService.
 */
class AppIndexingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            if (context == null || intent == null) {
                return
            }
            AppIndexingService.startService(context)
        } catch (e: Exception) {
            Crashlytics.logException(e)
        }
    }
}