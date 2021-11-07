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

import android.content.DialogInterface
import android.content.Intent
import android.graphics.*
import android.graphics.Color.rgb
import android.net.Uri
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import net.kourlas.voipms_sms.R
import kotlin.math.abs

/**
 * Applies a circular mask to a bitmap.
 */
fun applyCircularMask(bitmap: Bitmap): Bitmap {
    val output = Bitmap.createBitmap(
        bitmap.width,
        bitmap.height,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(output)
    val paint = Paint()
    val rect = Rect(0, 0, bitmap.width, bitmap.height)

    canvas.drawARGB(0, 0, 0, 0)
    canvas.drawCircle(
        (bitmap.width / 2).toFloat(),
        (bitmap.height / 2).toFloat(),
        (bitmap.width / 2).toFloat(), paint
    )
    paint.isAntiAlias = true
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(bitmap, rect, rect, paint)
    return output
}

/**
 * Applies a circular mask to a view.
 */
fun applyCircularMask(view: View) {
    view.outlineProvider = getOvalViewOutlineProvider()
    view.clipToOutline = true
}

/**
 * Gets a view outline provider for ovals.
 */
private fun getOvalViewOutlineProvider(): ViewOutlineProvider =
    object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) =
            outline.setOval(0, 0, view.width, view.height)
    }

/**
 * Applies a rectangular rounded corners mask to a view.
 */
fun applyRoundedCornersMask(view: View) {
    view.outlineProvider = getRoundRectViewOutlineProvider()
    view.clipToOutline = true
}

/**
 * Gets a view outline provider for rounded rectangles.
 */
private fun getRoundRectViewOutlineProvider(): ViewOutlineProvider =
    object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val pixels = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                22f,
                view.context.resources.displayMetrics
            )
            outline.setRoundRect(0, 0, view.width, view.height, pixels)
        }

    }

/**
 * Shows an alert dialog with the specified title, text, and buttons.
 */
fun showAlertDialog(
    activity: FragmentActivity,
    title: String?,
    text: String?,
    positiveButtonText: String? = null,
    positiveButtonAction: DialogInterface.OnClickListener? = null,
    negativeButtonText: String? = null,
    negativeButtonAction: DialogInterface.OnClickListener? = null
): AlertDialog? {
    if (!activity.isFinishing) {
        val builder = AlertDialog.Builder(activity)
        builder.setMessage(text)
        builder.setTitle(title)
        builder.setPositiveButton(positiveButtonText, positiveButtonAction)
        builder.setNegativeButton(negativeButtonText, negativeButtonAction)
        builder.setCancelable(false)
        return builder.show()
    }
    return null
}

/**
 * Shows a generic snackbar.
 */
fun showSnackbar(
    activity: FragmentActivity, viewId: Int,
    text: String, length: Int = Snackbar.LENGTH_LONG
): Snackbar? {
    if (!activity.isFinishing) {
        val view = activity.findViewById<View>(viewId)
        val snackbar = Snackbar.make(view, text, length)
        snackbar.show()
        return snackbar
    }
    return null
}

/**
 * Shows a generic snackbar with a button.
 */
fun showSnackbar(
    activity: FragmentActivity, viewId: Int,
    text: String,
    buttonText: String? = null,
    buttonAction: View.OnClickListener? = null,
    length: Int = Snackbar.LENGTH_LONG
): Snackbar? {
    if (!activity.isFinishing) {
        val view = activity.findViewById<View>(viewId)
        val snackbar = Snackbar.make(view, text, length)
        snackbar.setAction(buttonText, buttonAction)
        snackbar.show()
        return snackbar
    }
    return null
}

/**
 * Shows a snackbar requesting a permission with a button linking to the
 * application settings page.
 */
fun showPermissionSnackbar(
    activity: AppCompatActivity, viewId: Int,
    text: String
): Snackbar? {
    if (activity.isFinishing) {
        val view = activity.findViewById<View>(viewId)
        val snackbar = Snackbar.make(view, text, Snackbar.LENGTH_LONG)
        snackbar.setAction(R.string.settings) {
            val intent = Intent()
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            val uri = Uri.fromParts("package", activity.packageName, null)
            intent.data = uri
            activity.startActivity(intent)
        }
        snackbar.show()
        return snackbar
    }
    return null
}

/**
 * Shows a toast with the value of the specified exception and aborts the
 * specified activity.
 */
fun abortActivity(
    activity: FragmentActivity, ex: Exception,
    duration: Int = Toast.LENGTH_SHORT
) {
    logException(ex)
    Toast.makeText(
        activity,
        activity.getString(R.string.toast_error, ex.message),
        duration
    ).show()
    activity.finish()
}

/**
 * Gets a deterministically selected material design colour associated with
 * the specified phone number.
 */
fun getMaterialDesignColour(phoneNumber: String): Int {
    val colours = listOf(
        rgb(0xd5, 0x00, 0x00), rgb(0xc5, 0x11, 0x62),
        rgb(0xaa, 0x00, 0xff), rgb(0x62, 0x00, 0xea),
        rgb(0x30, 0x4f, 0xfe), rgb(0x29, 0x62, 0xff),
        rgb(0x00, 0x91, 0xea), rgb(0x00, 0xb8, 0xd4),
        rgb(0x00, 0xbf, 0xa5), rgb(0x00, 0xc8, 0x53),
        rgb(0x64, 0xdd, 0x17), rgb(0xae, 0xea, 0x00),
        rgb(0xff, 0xd6, 0x00), rgb(0xff, 0xab, 0x00),
        rgb(0xff, 0x6d, 0x00), rgb(0xdd, 0x2c, 0x00),
        rgb(0x3e, 0x27, 0x23), rgb(0x21, 0x21, 0x21),
        rgb(0x26, 0x32, 0x38)
    )
    return colours[abs(phoneNumber.hashCode()) % colours.size]
}
