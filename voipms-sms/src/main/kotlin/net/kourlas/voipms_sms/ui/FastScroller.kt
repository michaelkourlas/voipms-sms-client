/*
 * Copyright (C) 2015 The Android Open Source Project
 * Modifications copyright (C) 2019-2020 Michael Kourlas
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

package net.kourlas.voipms_sms.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.util.StateSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.View.OnLayoutChangeListener
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import net.kourlas.voipms_sms.R
import kotlin.math.max
import kotlin.math.min

/**
 * Adds a "fast-scroll" bar to the RecyclerView that shows the current position
 * within the view and allows quickly moving to another position by dragging
 * the scrollbar thumb up or down. As the thumb is dragged, we show a floating
 * bubble alongside it that shows extra information.
 */
@SuppressLint("InflateParams")
class FastScroller private constructor(
    private val mRv: RecyclerView,
    position: Int
) :
    RecyclerView.OnScrollListener(), OnLayoutChangeListener,
    RecyclerView.OnItemTouchListener {
    private val mContext: Context = mRv.context
    private val mTrackImageView: ImageView
    private val mThumbImageView: ImageView
    private val mPreviewTextView: TextView
    private val mTrackWidth: Int
    private val mThumbHeight: Int
    private val mPreviewHeight: Int
    private val mPreviewMinWidth: Int
    private val mPreviewMarginTop: Int
    private val mPreviewMarginLeftRight: Int
    private val mTouchSlop: Int
    private val mContainer = Rect()
    private val mHandler = Handler(Looper.myLooper()!!)

    // Whether to render the scrollbar on the right side (otherwise it'll be
    // on the left).
    private val mPosRight: Boolean

    // Whether the scrollbar is currently visible (it may still be animating).
    private var mVisible = false

    // Whether we are waiting to hide the scrollbar (i.e. scrolling has
    // stopped).
    private var mPendingHide = false

    // Whether the user is currently dragging the thumb up or down.
    private var mDragging = false

    // Animations responsible for hiding the scrollbar & preview. May be null.
    private var mHideAnimation: AnimatorSet? = null
    private val mHideTrackRunnable = Runnable {
        hide(true /* animate */)
        mPendingHide = false
    }
    private var mHidePreviewAnimation: ObjectAnimator? = null

    private// Conversation isn't long enough to scroll
    // Only enable scrollbars for conversations long enough that they would
    // require several flings to scroll through.
    val isEnabled: Boolean
        get() {
            val range = mRv.computeVerticalScrollRange()
            val extent = mRv.computeVerticalScrollExtent()
            if (range == 0 || extent == 0) {
                return false
            }
            val pages = range.toFloat() / extent
            return pages > MIN_PAGES_TO_ENABLE
        }

    init {
        mRv.addOnLayoutChangeListener(this)
        mRv.addOnScrollListener(this)
        mRv.addOnItemTouchListener(this)
        mRv.adapter?.registerAdapterDataObserver(
            object : AdapterDataObserver() {
                override fun onChanged() {
                    updateScrollPos()
                }
            })
        mPosRight = when (position) {
            POSITION_RIGHT_SIDE -> true
            POSITION_LEFT_SIDE -> false
            else -> throw Exception("Unrecognized position type")
        }
        // Cache the dimensions we'll need during layout
        val res = mContext.resources
        mTrackWidth = res.getDimensionPixelSize(R.dimen.fastscroll_track_width)
        mThumbHeight = res.getDimensionPixelSize(
            R.dimen.fastscroll_thumb_height
        )
        mPreviewHeight = res.getDimensionPixelSize(
            R.dimen.fastscroll_preview_height
        )
        mPreviewMinWidth = res.getDimensionPixelSize(
            R.dimen.fastscroll_preview_min_width
        )
        mPreviewMarginTop = res.getDimensionPixelOffset(
            R.dimen.fastscroll_preview_margin_top
        )
        mPreviewMarginLeftRight = res.getDimensionPixelOffset(
            R.dimen.fastscroll_preview_margin_left_right
        )
        mTouchSlop = res.getDimensionPixelOffset(R.dimen.fastscroll_touch_slop)
        val inflator = LayoutInflater.from(mContext)
        mTrackImageView = inflator.inflate(
            R.layout.fastscroll_track, null
        ) as ImageView
        mThumbImageView = inflator.inflate(
            R.layout.fastscroll_thumb, null
        ) as ImageView
        mPreviewTextView = inflator.inflate(
            R.layout.fastscroll_preview, null
        ) as TextView
        refreshConversationThemeColor()
        // Add the fast scroll views to the overlay, so they are rendered above
        // the list
        val mOverlay = mRv.overlay
        mOverlay.add(mTrackImageView)
        mOverlay.add(mThumbImageView)
        mOverlay.add(mPreviewTextView)
        hide(false /* animate */)
        mPreviewTextView.alpha = 0f
    }

    private fun refreshConversationThemeColor() {
        mPreviewTextView.background = if (mPosRight)
            ContextCompat.getDrawable(
                mContext,
                R.drawable.fastscroll_preview_right
            )
        else
            ContextCompat.getDrawable(
                mContext,
                R.drawable.fastscroll_preview_left
            )
        val drawable = StateListDrawable()
        drawable.addState(
            intArrayOf(android.R.attr.state_pressed),
            ContextCompat.getDrawable(
                mContext,
                R.drawable.fastscroll_thumb_pressed
            )
        )
        drawable.addState(
            StateSet.WILD_CARD,
            ContextCompat.getDrawable(
                mContext,
                R.drawable.fastscroll_thumb
            )
        )
        mThumbImageView.setImageDrawable(drawable)
    }

    override fun onScrollStateChanged(
        view: RecyclerView, newState: Int
    ) {
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
            // Only show the scrollbar once the user starts scrolling
            if (!mVisible && isEnabled) {
                show()
            }
            cancelAnyPendingHide()
        } else if (newState == RecyclerView.SCROLL_STATE_IDLE && !mDragging) {
            // Hide the scrollbar again after scrolling stops
            hideAfterDelay()
        }
    }

    private fun show() {
        if (mHideAnimation?.isRunning == true) {
            mHideAnimation?.cancel()
        }
        // Slide the scrollbar in from the side
        val trackSlide = ObjectAnimator.ofFloat(
            mTrackImageView, View.TRANSLATION_X, 0f
        )
        val thumbSlide = ObjectAnimator.ofFloat(
            mThumbImageView, View.TRANSLATION_X, 0f
        )
        val animation = AnimatorSet()
        animation.playTogether(trackSlide, thumbSlide)
        animation.duration = SHOW_ANIMATION_DURATION_MS.toLong()
        animation.start()
        mVisible = true
        updateScrollPos()
    }

    private fun hideAfterDelay() {
        cancelAnyPendingHide()
        mHandler.postDelayed(mHideTrackRunnable, HIDE_DELAY_MS.toLong())
        mPendingHide = true
    }

    private fun cancelAnyPendingHide() {
        if (mPendingHide) {
            mHandler.removeCallbacks(mHideTrackRunnable)
        }
    }

    private fun hide(animate: Boolean) {
        val hiddenTranslationX = if (mPosRight) mTrackWidth else -mTrackWidth
        if (animate) {
            // Slide the scrollbar off to the side
            val trackSlide = ObjectAnimator.ofFloat(
                mTrackImageView, View.TRANSLATION_X,
                hiddenTranslationX.toFloat()
            )
            val thumbSlide = ObjectAnimator.ofFloat(
                mThumbImageView, View.TRANSLATION_X,
                hiddenTranslationX.toFloat()
            )
            mHideAnimation = AnimatorSet().also {
                it.playTogether(trackSlide, thumbSlide)
                it.duration = HIDE_ANIMATION_DURATION_MS.toLong()
                it.start()
            }
        } else {
            mTrackImageView.translationX = hiddenTranslationX.toFloat()
            mThumbImageView.translationX = hiddenTranslationX.toFloat()
        }
        mVisible = false
    }

    private fun showPreview() {
        if (mHidePreviewAnimation?.isRunning == true) {
            mHidePreviewAnimation?.cancel()
        }
        mPreviewTextView.alpha = 1f
    }

    private fun hidePreview() {
        mHidePreviewAnimation = ObjectAnimator.ofFloat(
            mPreviewTextView, View.ALPHA, 0f
        ).also {
            it.duration = HIDE_ANIMATION_DURATION_MS.toLong()
            it.start()
        }
    }

    override fun onScrolled(
        view: RecyclerView, dx: Int, dy: Int
    ) {
        updateScrollPos()
    }

    private fun updateScrollPos() {
        if (!mVisible) {
            return
        }
        val verticalScrollLength = mContainer.height() - mThumbHeight
        val verticalScrollStart = mContainer.top + mThumbHeight / 2
        val scrollRatio = computeScrollRatio()
        val thumbCenterY =
            verticalScrollStart + (verticalScrollLength * scrollRatio).toInt()
        layoutThumb(thumbCenterY)
        if (mDragging) {
            updatePreviewText()
            layoutPreview(thumbCenterY)
        }
    }

    /**
     * Returns the current position in the conversation, as a value between
     * 0 and 1, inclusive. The top of the conversation is 0, the bottom is 1,
     * the exact middle is 0.5, and so on.
     */
    private fun computeScrollRatio(): Float {
        val range = mRv.computeVerticalScrollRange()
        val extent = mRv.computeVerticalScrollExtent()
        var offset = mRv.computeVerticalScrollOffset()
        if (range == 0 || extent == 0) {
            // If the conversation doesn't scroll, we're at the bottom.
            return 1.0f
        }
        val scrollRange = range - extent
        offset = min(offset, scrollRange)
        return offset / scrollRange.toFloat()
    }

    private fun updatePreviewText() {
        mRv.layoutManager.let {
            it as LinearLayoutManager
            val pos = it.findFirstVisibleItemPosition()
            if (pos == RecyclerView.NO_POSITION) {
                return
            }
            mRv.adapter?.let { provider ->
                provider as SectionTitleProvider
                mPreviewTextView.text = provider.getSectionTitle(pos)
            }
        }
    }

    override fun onInterceptTouchEvent(
        rv: RecyclerView, e: MotionEvent
    ): Boolean {
        if (!mVisible) {
            return false
        }
        // If the user presses down on the scroll thumb, we'll start
        // intercepting events from the RecyclerView so we can handle the move
        // events while they're dragging it up/down.
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (isInsideThumb(e.x, e.y)) {
                startDrag()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (mDragging) {
                    return true
                }
                if (mDragging) {
                    cancelDrag()
                }
                return false
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                if (mDragging) {
                    cancelDrag()
                }
                return false
            }
        }
        return false
    }

    private fun isInsideThumb(x: Float, y: Float): Boolean {
        val hitTargetLeft = mThumbImageView.left - mTouchSlop
        val hitTargetRight = mThumbImageView.right + mTouchSlop
        return if (x < hitTargetLeft || x > hitTargetRight) {
            false
        } else y >= mThumbImageView.top && y <= mThumbImageView.bottom
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        if (!mDragging) {
            return
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> handleDragMove(e.y)
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> cancelDrag()
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(
        disallowIntercept: Boolean
    ) {
    }

    private fun startDrag() {
        mDragging = true
        mThumbImageView.isPressed = true
        updateScrollPos()
        showPreview()
        cancelAnyPendingHide()
    }

    private fun handleDragMove(y: Float) {
        val verticalScrollLength = mContainer.height() - mThumbHeight
        val verticalScrollStart = mContainer.top + mThumbHeight / 2
        // Convert the desired position from px to a scroll position in the
        // conversation.
        var dragScrollRatio = (y - verticalScrollStart) / verticalScrollLength
        dragScrollRatio = max(dragScrollRatio, 0.0f)
        dragScrollRatio = min(dragScrollRatio, 1.0f)
        // Scroll the RecyclerView to a new position.
        mRv.adapter?.let {
            val itemCount = it.itemCount
            val itemPos = ((itemCount - 1) * dragScrollRatio).toInt()
            mRv.scrollToPosition(itemPos)
        }
    }

    private fun cancelDrag() {
        mDragging = false
        mThumbImageView.isPressed = false
        hidePreview()
        hideAfterDelay()
    }

    override fun onLayoutChange(
        v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int,
        oldTop: Int, oldRight: Int, oldBottom: Int
    ) {
        if (!mVisible) {
            hide(false /* animate */)
        }
        // The container is the size of the RecyclerView that's visible on
        // screen. We have to exclude the top padding, because it's usually
        // hidden behind the conversation action bar.
        mContainer.set(left, top + mRv.paddingTop, right, bottom)
        layoutTrack()
        updateScrollPos()
    }

    private fun layoutTrack() {
        val trackHeight = max(0, mContainer.height())
        val widthMeasureSpec = MeasureSpec.makeMeasureSpec(
            mTrackWidth, MeasureSpec.EXACTLY
        )
        val heightMeasureSpec = MeasureSpec.makeMeasureSpec(
            trackHeight, MeasureSpec.EXACTLY
        )
        mTrackImageView.measure(widthMeasureSpec, heightMeasureSpec)
        val left = if (mPosRight)
            mContainer.right - mTrackWidth
        else
            mContainer.left
        val top = mContainer.top
        val right = if (mPosRight)
            mContainer.right
        else
            mContainer.left + mTrackWidth
        val bottom = mContainer.bottom
        mTrackImageView.layout(left, top, right, bottom)
    }

    private fun layoutThumb(centerY: Int) {
        val widthMeasureSpec = MeasureSpec.makeMeasureSpec(
            mTrackWidth, MeasureSpec.EXACTLY
        )
        val heightMeasureSpec = MeasureSpec.makeMeasureSpec(
            mThumbHeight, MeasureSpec.EXACTLY
        )
        mThumbImageView.measure(widthMeasureSpec, heightMeasureSpec)
        val left = if (mPosRight)
            mContainer.right - mTrackWidth
        else
            mContainer.left
        val top = centerY - mThumbImageView.height / 2
        val right = if (mPosRight)
            mContainer.right
        else
            mContainer.left + mTrackWidth
        val bottom = top + mThumbHeight
        mThumbImageView.layout(left, top, right, bottom)
    }

    private fun layoutPreview(centerY: Int) {
        var widthMeasureSpec = MeasureSpec.makeMeasureSpec(
            mContainer.width(), MeasureSpec.AT_MOST
        )
        val heightMeasureSpec = MeasureSpec.makeMeasureSpec(
            mPreviewHeight, MeasureSpec.EXACTLY
        )
        mPreviewTextView.measure(widthMeasureSpec, heightMeasureSpec)
        // Ensure that the preview bubble is at least as wide as it is tall
        if (mPreviewTextView.measuredWidth < mPreviewMinWidth) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(
                mPreviewMinWidth, MeasureSpec.EXACTLY
            )
            mPreviewTextView.measure(widthMeasureSpec, heightMeasureSpec)
        }
        val previewMinY = mContainer.top + mPreviewMarginTop
        val left: Int
        val right: Int
        if (mPosRight) {
            right = mContainer.right - mTrackWidth - mPreviewMarginLeftRight
            left = right - mPreviewTextView.measuredWidth
        } else {
            left = mContainer.left + mTrackWidth + mPreviewMarginLeftRight
            right = left + mPreviewTextView.measuredWidth
        }
        var bottom = centerY
        var top = bottom - mPreviewTextView.measuredHeight
        if (top < previewMinY) {
            top = previewMinY
            bottom = top + mPreviewTextView.measuredHeight
        }
        mPreviewTextView.layout(left, top, right, bottom)
    }

    interface SectionTitleProvider {
        fun getSectionTitle(position: Int): String
    }

    companion object {
        const val POSITION_RIGHT_SIDE = 0
        const val POSITION_LEFT_SIDE = 1
        private const val MIN_PAGES_TO_ENABLE = 7
        private const val SHOW_ANIMATION_DURATION_MS = 150
        private const val HIDE_ANIMATION_DURATION_MS = 300
        private const val HIDE_DELAY_MS = 1500

        /**
         * Creates a [FastScroller] instance, attached to the provided
         * [RecyclerView].
         *
         * @param rv       the conversation RecyclerView
         * @param position where the scrollbar should appear (either
         * `POSITION_RIGHT_SIDE` or
         * `POSITION_LEFT_SIDE`)
         * @return a new FastScroller, or `null` if fast-scrolling is not
         * supported(the feature requires Jellybean MR2 or newer)
         */
        fun addTo(rv: RecyclerView, position: Int): FastScroller {
            return FastScroller(rv, position)
        }
    }
}