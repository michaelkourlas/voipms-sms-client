/*
 * VoIP.ms SMS
 * Copyright (C) 2020 Michael Kourlas
 *
 * Portions copyright (C) 2006 The Android Open Source Project (taken from
 * LinkMovementMethod.java)
 * Portions copyright (C) saket (taken from BetterLinkMovementMethod.java)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kourlas.voipms_sms.conversation

import android.annotation.SuppressLint
import android.content.Context
import android.text.Selection
import android.text.Spannable
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.appcompat.widget.AppCompatTextView

class MessageTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(
    context,
    attrs,
    defStyleAttr
) {
    private var longClick = false
    var messageLongClickListener: (() -> Unit)? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (!(event?.action == MotionEvent.ACTION_UP
                || event?.action == MotionEvent.ACTION_DOWN)
        ) {
            if (event?.action == MotionEvent.ACTION_CANCEL) {
                longClick = false
            }
            return super.onTouchEvent(event)
        }

        val x = event.x.toInt() - totalPaddingLeft + scrollX
        val y = event.y.toInt() - totalPaddingTop + scrollY
        val line = layout?.getLineForVertical(y)
            ?: return super.onTouchEvent(event)
        val off = layout?.getOffsetForHorizontal(line, x.toFloat())
            ?: return super.onTouchEvent(event)

        val buffer = text
        if (buffer !is Spannable) {
            return super.onTouchEvent(event)
        }

        val links = buffer.getSpans(off, off, ClickableSpan::class.java)

        if (links.isNotEmpty()) {
            val link = links[0]
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    if (!longClick) {
                        link.onClick(this)
                    }
                    longClick = false
                }
                MotionEvent.ACTION_DOWN -> {
                    Selection.setSelection(
                        buffer,
                        buffer.getSpanStart(link),
                        buffer.getSpanEnd(link)
                    )
                    postDelayed(
                        {
                            longClick = true
                            performHapticFeedback(
                                HapticFeedbackConstants.LONG_PRESS
                            )
                            messageLongClickListener?.invoke()
                        },
                        ViewConfiguration.getLongPressTimeout().toLong()
                    )
                }
            }
            return true
        } else {
            Selection.removeSelection(buffer)
        }

        return super.onTouchEvent(event)
    }
}