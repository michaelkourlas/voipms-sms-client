/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2021 Michael Kourlas
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
import android.graphics.*
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import net.kourlas.voipms_sms.BuildConfig
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.demo.getNewConversationContacts
import java.util.*

/**
 * Gets the name of a contact from the Android contacts provider, given a
 * phone number.
 */
fun getContactName(
    context: Context, phoneNumber: String,
    contactNameCache: MutableMap<String, String>? = null
): String? {
    try {
        if (contactNameCache != null && phoneNumber in contactNameCache) {
            return contactNameCache[phoneNumber]
        }
        if (BuildConfig.IS_DEMO) {
            for (contactItem in getNewConversationContacts(context)) {
                if (contactItem.primaryPhoneNumber == phoneNumber) {
                    return contactItem.name
                }
            }
            return null
        }

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME
            ),
            null, null, null
        )
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                val name = cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        ContactsContract.PhoneLookup.DISPLAY_NAME
                    )
                )
                cursor.close()
                if (contactNameCache != null && name != null) {
                    contactNameCache[phoneNumber] = name
                }
                return name
            } else {
                cursor.close()
            }
        }
        return null
    } catch (e: Exception) {
        return null
    }
}

/**
 * Gets the photo bitmap corresponding to the specified name and phone number
 * using the specified context. Provides a generic image if there is no photo
 * available. Uses the specified cache if one is provided.
 */
fun getContactPhotoBitmap(
    context: Context,
    name: String?,
    phoneNumber: String,
    size: Int,
    contactBitmapCache: MutableMap<String, Bitmap>? = null
): Bitmap {
    // Attempt to provide a contact photo.
    try {
        val cachedBitmap = contactBitmapCache?.get(phoneNumber)
        if (cachedBitmap != null) {
            return cachedBitmap
        }

        val photoUri = getContactPhotoUri(context, phoneNumber)
        if (photoUri != null) {
            val bitmap = getBitmapFromUri(context, Uri.parse(photoUri), size)
            if (bitmap != null) {
                if (contactBitmapCache != null) {
                    contactBitmapCache[phoneNumber] = bitmap
                }
                return bitmap
            }
        }
    } catch (e: Exception) {
    }

    return getGenericContactPhotoBitmap(context, name, phoneNumber, size)
}

/**
 * Retrieves a generic contact photo bitmap corresponding to the specified name
 * and phone number using the specified context.
 */
fun getGenericContactPhotoBitmap(
    context: Context,
    name: String?,
    phoneNumber: String,
    size: Int
): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(getMaterialDesignColour(getDigitsOfString(phoneNumber)))
    val canvas = Canvas(bitmap)

    val initial = getContactInitial(name)
    if (initial[0].isLetter()) {
        val paint = Paint()
        paint.color = Color.WHITE
        paint.typeface = Typeface.SANS_SERIF
        paint.textSize = size.toFloat() / 2
        paint.textAlign = Paint.Align.LEFT

        val textBounds = Rect()
        paint.getTextBounds(initial, 0, 1, textBounds)
        val x = canvas.width / 2f - textBounds.width() / 2f - textBounds.left
        val y =
            canvas.height / 2f + textBounds.height() / 2f - textBounds.bottom
        canvas.drawText(initial, x, y, paint)
    } else {
        val iconDrawable = ContextCompat.getDrawable(
            context,
            R.drawable.ic_account_circle_inverted_toolbar_24dp
        ) ?: return bitmap
        iconDrawable.setBounds(0, 0, size, size)
        iconDrawable.draw(canvas)
    }

    return bitmap
}

/**
 * Gets an adaptive bitmap corresponding to the specified phone number using the
 * specified context. Provides a generic image if there is no photo available.
 * Uses the specified cache if one is provided.
 */
fun getContactPhotoAdaptiveBitmap(
    context: Context,
    name: String?,
    phoneNumber: String,
    contactBitmapCache: MutableMap<String, Bitmap>? = null
): Bitmap {
    val bitmap = getContactPhotoBitmap(
        context,
        name,
        phoneNumber,
        context.resources.getDimensionPixelSize(
            R.dimen.adaptive_icon_drawable_inner
        ),
        contactBitmapCache
    )
    val adaptiveBitmap = Bitmap.createBitmap(
        context.resources.getDimensionPixelSize(
            R.dimen.adaptive_icon_drawable_outer
        ),
        context.resources.getDimensionPixelSize(
            R.dimen.adaptive_icon_drawable_outer
        ),
        Bitmap.Config.ARGB_8888
    )
    adaptiveBitmap.eraseColor(Color.TRANSPARENT)
    val adaptiveCanvas = Canvas(adaptiveBitmap)
    val left = (adaptiveBitmap.width - bitmap.width) / 2f
    val top = (adaptiveBitmap.height - bitmap.height) / 2f
    adaptiveCanvas.drawBitmap(bitmap, left, top, null)
    return adaptiveBitmap
}

/**
 * Gets the photo URI corresponding to the specified phone number using the
 * specified context. Uses the specified cache if one is provided.
 */
fun getContactPhotoUri(
    context: Context,
    phoneNumber: String,
    contactPhotoUriCache: MutableMap<String, String>? = null
): String? {
    try {
        if (contactPhotoUriCache != null && phoneNumber in contactPhotoUriCache) {
            return contactPhotoUriCache[phoneNumber]
        }

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val photoUri = getContactPhotoUri(context, uri)
        if (photoUri != null) {
            if (contactPhotoUriCache != null) {
                contactPhotoUriCache[phoneNumber] = photoUri
            }
            return photoUri
        }
        return null
    } catch (e: Exception) {
        return null
    }
}

/**
 * Gets a URI pointing to a contact's photo, given the URI for that contact.
 */
fun getContactPhotoUri(
    context: Context,
    uri: Uri,
    contactPhotoUriCache: MutableMap<Uri, String>? = null
): String? {
    try {
        if (contactPhotoUriCache != null && uri in contactPhotoUriCache) {
            return contactPhotoUriCache[uri]
        }

        val cursor = context.contentResolver.query(
            uri, arrayOf(
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
            ),
            null, null, null
        )
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                val photoUri = cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
                    )
                )
                cursor.close()
                if (contactPhotoUriCache != null) {
                    contactPhotoUriCache[uri] = photoUri
                }
                return photoUri
            } else {
                cursor.close()
            }
        }
        return null
    } catch (e: Exception) {
        return null
    }
}

/**
 * Retrieves a bitmap from the specified URI using the specified context.
 * Ensures that the bitmap is roughly the same size as the contact photo badge
 * control.
 *
 * Adapted from https://developer.android.com/topic/performance/graphics/load-bitmap.html.
 * This code is therefore licensed under the Apache 2.0 license and is
 * copyrighted by Google.
 */
fun getBitmapFromUri(context: Context, uri: Uri, size: Int): Bitmap? {
    try {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(
            context.contentResolver.openInputStream(uri),
            null, options
        )

        options.inJustDecodeBounds = false
        options.inSampleSize = calculateInSampleSize(options, size, size)
        val bitmap = BitmapFactory.decodeStream(
            context.contentResolver.openInputStream(uri), null, options
        )
            ?: return null
        return Bitmap.createScaledBitmap(bitmap, size, size, true)
    } catch (e: Exception) {
        return null
    }
}

/**
 * Calculates the inSampleSize required to ensure that the bitmap whose raw
 * dimensions are contained in the specified options is decoded at roughly
 * the specified width and height.
 *
 * Adapted from https://developer.android.com/topic/performance/graphics/load-bitmap.html.
 * This code is therefore licensed under the Apache 2.0 license and is
 * copyrighted by Google.
 */
fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int, reqHeight: Int
): Int {
    // Raw height and width of image
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        // Calculate the largest inSampleSize value that is a power of 2 and
        // keeps both height and width larger than the requested height and
        // width
        while (halfHeight / inSampleSize >= reqHeight
            && halfWidth / inSampleSize >= reqWidth
        ) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}

/**
 * Gets the initial corresponding to the specified name.
 */
fun getContactInitial(name: String?): String =
    name?.uppercase(Locale.getDefault())?.getOrNull(0)?.let {
        when {
            it.isLetter() -> it.toString()
            it.isDigit() -> "#"
            else -> null
        }
    } ?: "…"
