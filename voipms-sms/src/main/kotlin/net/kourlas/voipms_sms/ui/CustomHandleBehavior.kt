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

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.support.annotation.AnimatorRes
import android.view.View

import com.futuremind.recyclerviewfastscroll.viewprovider.ViewBehavior
import com.futuremind.recyclerviewfastscroll.viewprovider.VisibilityAnimationManager

/**
 * Provides scroll bar behaviour consistent with the Android Messages
 * design aesthetic.
 */
class CustomHandleBehavior(
    private val visibilityManager: VisibilityAnimationManager,
    private val grabManager: CustomHandleBehavior.HandleAnimationManager) : ViewBehavior {

    private var isGrabbed: Boolean = false

    override fun onHandleGrabbed() {
        isGrabbed = true
        visibilityManager.show()
        grabManager.onGrab()
    }

    override fun onHandleReleased() {
        isGrabbed = false
        visibilityManager.hide()
        grabManager.onRelease()
    }

    override fun onScrollStarted() = visibilityManager.show()

    override fun onScrollFinished() {
        if (!isGrabbed) visibilityManager.hide()
    }

    class HandleAnimationManager private constructor(handle: View,
                                                     @AnimatorRes grabAnimator: Int,
                                                     @AnimatorRes releaseAnimator: Int) {

        private var grabAnimator: AnimatorSet? = null
        private var releaseAnimator: AnimatorSet? = null

        init {
            if (grabAnimator != -1) {
                this.grabAnimator = AnimatorInflater.loadAnimator(
                    handle.context, grabAnimator) as AnimatorSet
                this.grabAnimator!!.setTarget(handle)
            }
            if (releaseAnimator != -1) {
                this.releaseAnimator = AnimatorInflater.loadAnimator(
                    handle.context, releaseAnimator) as AnimatorSet
                this.releaseAnimator!!.setTarget(handle)
            }
        }

        fun onGrab() {
            if (releaseAnimator != null) {
                releaseAnimator!!.cancel()
            }
            if (grabAnimator != null) {
                grabAnimator!!.start()
            }
        }

        fun onRelease() {
            if (grabAnimator != null) {
                grabAnimator!!.cancel()
            }
            if (releaseAnimator != null) {
                releaseAnimator!!.start()
            }
        }

        class Builder(private val handle: View) {
            private var grabAnimator: Int = 0
            private var releaseAnimator: Int = 0

            fun withGrabAnimator(@AnimatorRes grabAnimator: Int): Builder {
                this.grabAnimator = grabAnimator
                return this
            }

            fun withReleaseAnimator(
                @AnimatorRes releaseAnimator: Int): Builder {
                this.releaseAnimator = releaseAnimator
                return this
            }

            fun build(): HandleAnimationManager = HandleAnimationManager(handle,
                                                                         grabAnimator,
                                                                         releaseAnimator)
        }
    }

}
