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

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.*
import android.graphics.Color.rgb
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.support.design.widget.Snackbar
import android.support.v7.app.AlertDialog
import android.view.View
import android.view.ViewOutlineProvider
import net.kourlas.voipms_sms.R
import java.lang.Math.abs

/**
 * Applies a circular mask to a bitmap.
 *
 * @param bitmap The specified bitmap.
 * @return The bitmap with a circular mask.
 */
fun applyCircularMask(bitmap: Bitmap): Bitmap {
    val output = Bitmap.createBitmap(bitmap.width,
                                     bitmap.height,
                                     Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint()
    val rect = Rect(0, 0, bitmap.width, bitmap.height)

    canvas.drawARGB(0, 0, 0, 0)
    canvas.drawCircle((bitmap.width / 2).toFloat(),
                      (bitmap.height / 2).toFloat(),
                      (bitmap.width / 2).toFloat(), paint)
    paint.isAntiAlias = true
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(bitmap, rect, rect, paint)
    return output
}

/**
 * Applies a circular mask to a view.
 *
 * Note that this method only works on Lollipop and above; it will
 * silently fail on older versions.
 *
 * @param view The specified view.
 */
fun applyCircularMask(view: View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        view.outlineProvider = getOvalViewOutlineProvider()
        view.clipToOutline = true
    }
}

/**
 * Gets a view outline provider for ovals.
 *
 * @return A a view outline provider for ovals.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
private fun getOvalViewOutlineProvider(): ViewOutlineProvider = object : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) = outline.setOval(0,
                                                                            0,
                                                                            view.width,
                                                                            view.height)
}

/**
 * Applies a rectangular rounded corners mask to a view.
 *
 * Note that this method only works on Lollipop and above; it will
 * silently fail on older versions.
 *
 * @param view The specified view.
 */
fun applyRoundedCornersMask(view: View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        view.outlineProvider = getRoundRectViewOutlineProvider()
        view.clipToOutline = true
    }
}

/**
 * Gets a view outline provider for rounded rectangles.
 *
 * @return A a view outline provider for rounded rectangles.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
private fun getRoundRectViewOutlineProvider(): ViewOutlineProvider = object : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) = outline
        .setRoundRect(0, 0, view.width, view.height,
                      15f)
}

/**
 * Shows an alert dialog with the specified title, text, and buttons.
 *
 * @param context The context to use.
 * @param title The specified title.
 * @param text The specified text.
 * @param positiveButtonText The text of the positive button.
 * @param positiveButtonAction The action associated with the positive button.
 * @param negativeButtonText The text of the negative button.
 * @param negativeButtonAction The action associated with the negative button.
 * @return The created alert dialog.
 */
fun showAlertDialog(context: Context, title: String?, text: String?,
                    positiveButtonText: String? = null,
                    positiveButtonAction: DialogInterface.OnClickListener? = null,
                    negativeButtonText: String? = null,
                    negativeButtonAction: DialogInterface.OnClickListener? = null): AlertDialog {
    val builder = AlertDialog.Builder(context, R.style.DialogTheme)
    builder.setMessage(text)
    builder.setTitle(title)
    builder.setPositiveButton(positiveButtonText, positiveButtonAction)
    builder.setNegativeButton(negativeButtonText, negativeButtonAction)
    builder.setCancelable(false)
    return builder.show()
}

/**
 * Shows an information dialog with the specified text.
 *
 * @param context The context to use.
 * @param text The specified text.
 * @return The dialog.
 */
fun showInfoDialog(context: Context,
                   text: String?): AlertDialog = showAlertDialog(context, null,
                                                                 text,
                                                                 context.getString(
                                                                     R.string.ok),
                                                                 null, null,
                                                                 null)

/**
 * Shows an information dialog with the specified title and text.
 *
 * @param context The context to use.
 * @param title The specified title.
 * @param text The specified text.
 * @return The dialog.
 */
fun showInfoDialog(context: Context, title: String?,
                   text: String?): AlertDialog = showAlertDialog(context, title,
                                                                 text,
                                                                 context.getString(
                                                                     R.string.ok),
                                                                 null, null,
                                                                 null)

/**
 * Shows a generic snackbar.
 *
 * @param activity The activity to use.
 * @param viewId The ID of the view to add the snackbar to.
 * @param text The text to show.
 * @return The snackbar.
 */
fun showSnackbar(activity: Activity, viewId: Int, text: String): Snackbar {
    val view = activity.findViewById<View>(viewId)
    val snackbar = Snackbar.make(view, text, Snackbar.LENGTH_LONG)
    snackbar.show()
    return snackbar
}

/**
 * Shows a snackbar requesting a permission with a button linking to the
 * application settings page.
 *
 * @param activity The activity to use.
 * @param viewId The ID of the view to add the snackbar to.
 * @param text The text to show.
 * @return The snackbar.
 */
fun showPermissionSnackbar(activity: Activity, viewId: Int,
                           text: String): Snackbar {
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

/**
 * Gets a deterministically selected material design colour associated with
 * the specified phone number.
 *
 * @param phoneNumber The specified phone number
 * @return The selected colour.
 */
fun getMaterialDesignColour(phoneNumber: String): Int {
    val colours = listOf(rgb(0xd5, 0x00, 0x00), rgb(0xc5, 0x11, 0x62),
                         rgb(0xaa, 0x00, 0xff), rgb(0x62, 0x00, 0xea),
                         rgb(0x30, 0x4f, 0xfe), rgb(0x29, 0x62, 0xff),
                         rgb(0x00, 0x91, 0xea), rgb(0x00, 0xb8, 0xd4),
                         rgb(0x00, 0xbf, 0xa5), rgb(0x00, 0xc8, 0x53),
                         rgb(0x64, 0xdd, 0x17), rgb(0xae, 0xea, 0x00),
                         rgb(0xff, 0xd6, 0x00), rgb(0xff, 0xab, 0x00),
                         rgb(0xff, 0x6d, 0x00), rgb(0xdd, 0x2c, 0x00),
                         rgb(0x3e, 0x27, 0x23), rgb(0x21, 0x21, 0x21),
                         rgb(0x26, 0x32, 0x38))
    return colours[abs(phoneNumber.hashCode()) % colours.size]
}