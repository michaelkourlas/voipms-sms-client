/*
 * VoIP.ms SMS
 * Copyright (C) 2015-2019 Michael Kourlas
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
 * Returns the difference between two dates' year, month, or day, whichever
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
 * conversation view.
 */
fun getScrollBarDate(date: Date): String {
    val calendar = Calendar.getInstance()
    calendar.time = date

    if (compareDateWithoutTime(Calendar.getInstance(), calendar) == 0) {
        // Today: "h:mm a"
        val format = SimpleDateFormat(
            "h:mm a",
            Locale.getDefault()
        )
        return format.format(date)
    }

    if (Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
        == calendar.get(
            Calendar.WEEK_OF_YEAR
        )
        && Calendar.getInstance().get(
            Calendar.YEAR
        )
        == calendar.get(Calendar.YEAR)
    ) {
        // This week: "EEE"
        val format = SimpleDateFormat(
            "EEE",
            Locale.getDefault()
        )
        return format.format(date)
    }

    // Any: "MMM d"
    val format = SimpleDateFormat(
        "MMM d",
        Locale.getDefault()
    )
    return format.format(date)
}

/**
 * Get the formatted date used for the conversations view.
 */
fun getConversationsViewDate(context: Context, date: Date): String {
    val calendar = Calendar.getInstance()
    calendar.time = date

    val oneMinuteAgo = Calendar.getInstance()
    oneMinuteAgo.add(Calendar.MINUTE, -1)
    if (oneMinuteAgo.time.before(date)) {
        // Last minute: "now"
        return context.getString(R.string.utils_date_just_now)
    }

    val oneHourAgo = Calendar.getInstance()
    oneHourAgo.add(Calendar.HOUR_OF_DAY, -1)
    if (oneHourAgo.time.before(date)) {
        // Last hour: "X min"
        val minutes = (Calendar.getInstance().time.time
            - calendar.time.time) / (1000 * 60)
        return "$minutes " + context.getString(
            R.string.utils_date_minutes_ago
        )
    }

    if (compareDateWithoutTime(Calendar.getInstance(), calendar) == 0) {
        // Today: "h:mm a"
        val format = SimpleDateFormat(
            "h:mm a",
            Locale.getDefault()
        )
        return format.format(date)
    }

    if (Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
        == calendar.get(
            Calendar.WEEK_OF_YEAR
        )
        && Calendar.getInstance().get(
            Calendar.YEAR
        )
        == calendar.get(Calendar.YEAR)
    ) {
        // This week: "EEE"
        val format = SimpleDateFormat(
            "EEE",
            Locale.getDefault()
        )
        return format.format(date)
    }

    if (Calendar.getInstance().get(Calendar.YEAR)
        == calendar.get(Calendar.YEAR)
    ) {
        // This year: "MMM d"
        val format = SimpleDateFormat(
            "MMM d",
            Locale.getDefault()
        )
        return format.format(date)
    }

    // Any: "MMM d, yyyy"
    val format = SimpleDateFormat(
        "MMM d, yyyy",
        Locale.getDefault()
    )
    return format.format(date)
}

/**
 * Get the formatted date used for the start of a portion of a conversation in
 * the conversation view.
 */
fun getConversationViewTopDate(date: Date): String {
    val calendar = Calendar.getInstance()
    calendar.time = date

    if (compareDateWithoutTime(Calendar.getInstance(), calendar) == 0) {
        // Today: "h:mm a"
        val format = SimpleDateFormat(
            "h:mm a",
            Locale.getDefault()
        )
        return format.format(date)
    }

    if (Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
        == calendar.get(
            Calendar.WEEK_OF_YEAR
        )
        && Calendar.getInstance().get(
            Calendar.YEAR
        )
        == calendar.get(Calendar.YEAR)
    ) {
        // This week: "EEEEEEEEE • h:mm a"
        val format = SimpleDateFormat(
            "EEEEEEEEE • h:mm a",
            Locale.getDefault()
        )
        return format.format(date)
    }

    if (Calendar.getInstance().get(Calendar.YEAR)
        == calendar.get(Calendar.YEAR)
    ) {
        // This year: "EEEEEEEEE, MMM d • h:mm a"
        val format = SimpleDateFormat(
            "EEEEEEEEE, MMM d • h:mm a",
            Locale.getDefault()
        )
        return format.format(date)
    }

    // Any: "EEEEEEEEE, MMM d, yyyy • h:mm a"
    val format = SimpleDateFormat(
        "EEEEEEEEE, MMM d, yyyy • h:mm a",
        Locale.getDefault()
    )
    return format.format(date)
}

/**
 * Get the formatted date used for an individual message in the conversation
 * view.
 */
fun getConversationViewDate(context: Context, date: Date): String {
    val calendar = Calendar.getInstance()
    calendar.time = date

    val oneMinuteAgo = Calendar.getInstance()
    oneMinuteAgo.add(Calendar.MINUTE, -1)
    if (oneMinuteAgo.time.before(date)) {
        // Last minute: "now"
        return context.getString(R.string.utils_date_just_now)
    }

    val oneHourAgo = Calendar.getInstance()
    oneHourAgo.add(Calendar.HOUR_OF_DAY, -1)
    if (oneHourAgo.time.before(date)) {
        // Last hour: "X min"
        val minutes = (Calendar.getInstance().time.time
            - calendar.time.time) / (1000 * 60)
        return "$minutes " + context.getString(
            R.string.utils_date_minutes_ago
        )
    }

    if (compareDateWithoutTime(Calendar.getInstance(), calendar) == 0) {
        // Today: "h:mm a"
        val format = SimpleDateFormat(
            "h:mm a",
            Locale.getDefault()
        )
        return format.format(date)
    }

    // Any: "MMM d, h:mm a"
    val format = SimpleDateFormat(
        "MMM d, h:mm a",
        Locale.getDefault()
    )
    return format.format(date)
}
