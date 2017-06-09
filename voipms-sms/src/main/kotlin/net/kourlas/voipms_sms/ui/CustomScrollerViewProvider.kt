/*
 * Copyright (C) 2015 Future Mind.
 * Modifications copyright (C) 2017 Michael Kourlas.
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

package net.kourlas.voipms_sms.ui

import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import com.futuremind.recyclerviewfastscroll.R
import com.futuremind.recyclerviewfastscroll.Utils
import com.futuremind.recyclerviewfastscroll.viewprovider.DefaultBubbleBehavior
import com.futuremind.recyclerviewfastscroll.viewprovider.ScrollerViewProvider
import com.futuremind.recyclerviewfastscroll.viewprovider.ViewBehavior
import com.futuremind.recyclerviewfastscroll.viewprovider.VisibilityAnimationManager

/**
 * Provides scroll bar behaviour consistent with the Android Messages
 * design aesthetic.
 */
class CustomScrollerViewProvider : ScrollerViewProvider() {
    private lateinit var bubble: View
    private lateinit var handle: View

    override fun provideHandleView(container: ViewGroup): View {
        handle = View(context)

        Utils.setBackground(handle, ContextCompat.getDrawable(
            context,
            net.kourlas.voipms_sms.R.drawable.recycler_view_scrollbar_handle))
        val params = ViewGroup.LayoutParams(
            context.resources.getDimensionPixelSize(
                net.kourlas.voipms_sms.R.dimen.conversation_scrollbar_width),
            context.resources.getDimensionPixelSize(
                net.kourlas.voipms_sms.R.dimen.conversation_scrollbar_height))
        handle.layoutParams = params

        return handle
    }

    override fun provideBubbleView(container: ViewGroup): View {
        bubble = LayoutInflater.from(context).inflate(
            R.layout.fastscroll__default_bubble, container, false)
        return bubble
    }

    override fun provideBubbleTextView(): TextView {
        return bubble as TextView
    }

    override fun getBubbleOffset(): Int {
        return if (scroller.isVertical) {
            (handle.height.toFloat() / 2f - bubble.height).toInt()
        } else {
            (handle.width.toFloat() / 2f - bubble.width).toInt()
        }
    }

    override fun provideHandleBehavior(): ViewBehavior? {
        return CustomHandleBehavior(
            VisibilityAnimationManager.Builder(handle)
                .build(),
            CustomHandleBehavior.HandleAnimationManager.Builder(handle)
                .withGrabAnimator(-1)
                .withReleaseAnimator(-1)
                .build())
    }

    override fun provideBubbleBehavior(): ViewBehavior? {
        return DefaultBubbleBehavior(
            VisibilityAnimationManager.Builder(bubble).withPivotX(
                1f).withPivotY(1f).build())
    }
}

