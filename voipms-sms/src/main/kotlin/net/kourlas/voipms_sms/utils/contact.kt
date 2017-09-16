/*
 * VoIP.ms SMS
 * Copyright (C) 2017 Michael Kourlas
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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import net.kourlas.voipms_sms.R


/**
 * Gets the name of a contact from the Android contacts provider, given a
 * phone number.
 *
 * @param context The context to use.
 * @param phoneNumber The specified phone number.
 * @return The name of the contact from the Android contacts provider.
 */
fun getContactName(context: Context, phoneNumber: String,
                   contactNameCache: MutableMap<String, String>? = null): String? {
    try {
        if (contactNameCache != null && phoneNumber in contactNameCache) {
            return contactNameCache[phoneNumber]
        }

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber))
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                val name = cursor.getString(cursor.getColumnIndex(
                    ContactsContract.PhoneLookup.DISPLAY_NAME))
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
 * Gets the photo bitmap corresponding to the specified phone number using the
 * specified context. Uses the specified cache if one is provided.
 *
 * @param context The specified context.
 * @param phoneNumber The specified phone number.
 * @param contactBitmapCache The specified cache.
 */
fun getContactPhotoBitmap(context: Context, phoneNumber: String,
                          contactBitmapCache: MutableMap<String, Bitmap>? = null): Bitmap? {
    try {
        if (contactBitmapCache != null && phoneNumber in contactBitmapCache) {
            return contactBitmapCache[phoneNumber]
        }

        val photoUri = getContactPhotoUri(context, phoneNumber)
        if (photoUri != null) {
            val bitmap = getBitmapFromUri(context, Uri.parse(photoUri))
            if (bitmap != null) {
                if (contactBitmapCache != null) {
                    contactBitmapCache[phoneNumber] = bitmap
                }
                return bitmap
            }
        }
        return null
    } catch (e: Exception) {
        return null
    }
}

/**
 * Gets the photo URI corresponding to the specified phone number using the
 * specified context. Uses the specified cache if one is provided.
 *
 * @param context The specified context.
 * @param phoneNumber The specified phone number.
 * @param contactPhotoUriCache The specified cache.
 */
fun getContactPhotoUri(context: Context, phoneNumber: String,
                       contactPhotoUriCache: MutableMap<String, String>? = null): String? {
    try {
        if (contactPhotoUriCache != null && phoneNumber in contactPhotoUriCache) {
            return contactPhotoUriCache[phoneNumber]
        }

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber))
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
 *
 * @param context The context to use.
 * @param uri The specified URI.
 * @return A URI pointing to the contact's photo.
 */
fun getContactPhotoUri(context: Context, uri: Uri,
                       contactPhotoUriCache: MutableMap<Uri, String>? = null): String? {
    try {
        if (contactPhotoUriCache != null && uri in contactPhotoUriCache) {
            return contactPhotoUriCache[uri]
        }

        val cursor = context.contentResolver.query(
            uri, arrayOf(ContactsContract.PhoneLookup._ID,
                         ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
            null, null, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                val photoUri = cursor.getString(cursor.getColumnIndex(
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI))
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
 * Adapted from https://developer.android.com/topic/performance/graphics/load-bitmap.html
 *
 * @param context The specified context.
 * @param uri The specified URI.
 */
fun getBitmapFromUri(context: Context, uri: Uri): Bitmap? = try {
    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = true
    BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri),
                               null, options)

    val size = context.resources.getDimensionPixelSize(
        R.dimen.contact_badge)
    options.inJustDecodeBounds = false
    options.inSampleSize = calculateInSampleSize(options, size, size)
    BitmapFactory.decodeStream(
        context.contentResolver.openInputStream(uri), null, options)
} catch (e: Exception) {
    null
}

/**
 * Calculates the inSampleSize required to ensure that the bitmap whose raw
 * dimensions are contained in the specified options is decoded at roughly
 * the specified width and height.
 *
 * Adapted from https://developer.android.com/topic/performance/graphics/load-bitmap.html
 *
 * @param options The options containing the bitmap's raw width and height.
 * @param reqWidth The specified width.
 * @param reqHeight The specified height.
 * @return The inSampleSize required to ensure that the bitmap whose raw
 * dimensions are contained in the specified options is decoded at roughly
 * the specified width and height.
 */
fun calculateInSampleSize(options: BitmapFactory.Options,
                          reqWidth: Int, reqHeight: Int): Int {
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
               && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}

/**
 * Gets the contact initial corresponding to the specified name and phone
 * number. If a name is provided, the first letter of the name is used.
 * Otherwise, the first number in the phone number is used.
 *
 * If the first letter or number does not exist, the ellipsis character is
 * used.
 *
 * @param name The specified name.
 * @param phoneNumber The specified phone number.
 */
fun getContactInitial(name: String?, phoneNumber: String): String =
    if (name == null || name == phoneNumber) {
        getDigitsOfString(phoneNumber).getOrNull(0)?.toString() ?: "…"
    } else {
        name.toUpperCase().getOrNull(0)?.toString() ?: "…"
    }
