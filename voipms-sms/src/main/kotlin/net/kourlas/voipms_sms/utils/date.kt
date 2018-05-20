/*
 * VoIP.ms SMS
 * Copyright (C) 2015-2017 Michael Kourlas
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

package net.kourlas.voipms_sms.utils

import android.content.Context
import net.kourlas.voipms_sms.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * Returns a comparison value for two dates (excluding times).
 *
 * @param first The first date.
 * @param second The second date.
 * @return The difference between the dates' year, month, or day, whichever
 * differs first.
 */
private fun compareDateWithoutTime(first: Calendar, second: Calendar): Int {
    if (first.get(Calendar.YEAR) != second.get(Calendar.YEAR)) {
        return first.get(Calendar.YEAR) - second.get(Calendar.YEAR)
    }
    if (first.get(Calendar.MONTH) != second.get(Calendar.MONTH)) {
        return first.get(Calendar.MONTH) - second.get(Calendar.MONTH)
    }
    return first.get(Calendar.DAY_OF_MONTH) - second.get(Calendar.DAY_OF_MONTH)
}

/**
 * Get the formatted date used for the scroll bar on the recycler view in the
 * ConversationActivity and NewConversationActivity.
 *
 * @param date The date to format.
 * @return The formatted date.
 */
fun getScrollBarDate(date: Date): String {
    val calendar = Calendar.getInstance()
    calendar.time = date

    if (compareDateWithoutTime(Calendar.getInstance(), calendar) == 0) {
        // Today: h:mm a
        val format = SimpleDateFormat("h:mm a",
                                      Locale.getDefault())
        return format.format(date)
    }

    if (Calendar.getInstance().get(Calendar.WEEK_OF_YEAR) == calendar.get(
            Calendar.WEEK_OF_YEAR) && Calendar.getInstance().get(
            Calendar.YEAR) == calendar.get(Calendar.YEAR)) {
        // This week: EEE
        val format = SimpleDateFormat("EEE",
                                      Locale.getDefault())
        return format.format(date)
    }

    if (Calendar.getInstance().get(Calendar.YEAR) == calendar.get(
            Calendar.YEAR)) {
        // This year: MMM
        val format = SimpleDateFormat("MMM",
                                      Locale.getDefault())
        return format.format(date)
    }

    // Any: yyyy
    val format = SimpleDateFormat("yyyy",
                                  Locale.getDefault())
    return format.format(date)
}

/**
 * Formats a date for display in the application.
 *
 * @param context The specified context.
 * @param date The specified date.
 * @param hideTime If true, omits the time from the formatted date.
 * @return The formatted date.
 */
fun getFormattedDate(context: Context, date: Date, hideTime: Boolean): String {
    val calendar = Calendar.getInstance()
    calendar.time = date

    val oneMinuteAgo = Calendar.getInstance()
    oneMinuteAgo.add(Calendar.MINUTE, -1)
    if (oneMinuteAgo.time.before(date)) {
        // Last minute: X seconds ago
        val seconds = (Calendar.getInstance().time.time
                       - calendar.time.time) / 1000
        return if (seconds < 10) {
            context.getString(R.string.utils_date_just_now)
        } else {
            seconds.toString() + " " + context.getString(
                R.string.utils_date_seconds_ago)
        }
    }

    val oneHourAgo = Calendar.getInstance()
    oneHourAgo.add(Calendar.HOUR_OF_DAY, -1)
    if (oneHourAgo.time.before(date)) {
        // Last hour: X minutes ago
        val minutes = (Calendar.getInstance().time.time
                       - calendar.time.time) / (1000 * 60)
        return if (minutes == 1L) {
            context.getString(R.string.utils_date_one_minute_ago)
        } else {
            minutes.toString() + " " + context.getString(
                R.string.utils_date_minutes_ago)
        }
    }

    if (compareDateWithoutTime(Calendar.getInstance(), calendar) == 0) {
        // Today: h:mm a
        val format = SimpleDateFormat("h:mm a",
                                      Locale.getDefault())
        return format.format(date)
    }

    if (Calendar.getInstance().get(Calendar.WEEK_OF_YEAR) == calendar.get(
            Calendar.WEEK_OF_YEAR) && Calendar.getInstance().get(
            Calendar.YEAR) == calendar.get(Calendar.YEAR)) {
        return if (hideTime) {
            // This week: EEE
            val format = SimpleDateFormat("EEE",
                                          Locale.getDefault())
            format.format(date)
        } else {
            // This week: EEE h:mm a
            val format = SimpleDateFormat("EEE h:mm a",
                                          Locale.getDefault())
            format.format(date)
        }
    }

    if (Calendar.getInstance().get(Calendar.YEAR) == calendar.get(
            Calendar.YEAR)) {
        return if (hideTime) {
            // This year: MMM d
            val format = SimpleDateFormat("MMM d",
                                          Locale.getDefault())
            format.format(date)
        } else {
            // This year: MMM d h:mm a
            val format = SimpleDateFormat("MMM d, h:mm a",
                                          Locale.getDefault())
            format.format(date)
        }
    }

    return if (hideTime) {
        // Any: MMM d, yyyy
        val format = SimpleDateFormat("MMM d, yyyy",
                                      Locale.getDefault())
        format.format(date)
    } else {
        // Any: MMM d, yyyy h:mm a
        val format = SimpleDateFormat("MMM d, yyyy, h:mm a",
                                      Locale.getDefault())
        format.format(date)
    }
}
