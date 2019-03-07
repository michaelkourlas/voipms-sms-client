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

package net.kourlas.voipms_sms.conversations

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

/**
 * Describes behaviour of the new conversation floating action button on the
 * conversations view.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class FloatingActionButtonBehaviour(
    context: Context,
    attrs: AttributeSet) : CoordinatorLayout.Behavior<FloatingActionButton>() {
    override fun layoutDependsOn(
        parent: CoordinatorLayout,
        child: FloatingActionButton,
        dependency: View): Boolean =
        dependency is Snackbar.SnackbarLayout

    override fun onDependentViewChanged(
        parent: CoordinatorLayout,
        child: FloatingActionButton,
        dependency: View): Boolean {
        val translationY = Math.min(0f, dependency.translationY
                                        - dependency.height)
        child.translationY = translationY
        return true
    }

    override fun onDependentViewRemoved(
        parent: CoordinatorLayout,
        child: FloatingActionButton,
        dependency: View) {
        child.translationY = 0f
    }
}
