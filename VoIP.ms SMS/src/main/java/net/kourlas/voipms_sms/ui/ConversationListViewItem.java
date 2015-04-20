/*
 * VoIP.ms SMS
 * Copyright © 2015 Michael Kourlas
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.kourlas.voipms_sms.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;
import com.example.android.customchoicelist.CheckableLinearLayout;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.adapters.ConversationListViewAdapter;

public class ConversationListViewItem extends CheckableLinearLayout {
    public ConversationListViewItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setChecked(boolean b) {
        super.setChecked(b);

        int viewType = (Integer) getTag();
        TextView textView = (TextView) this.findViewById(R.id.text);
        if (viewType == ConversationListViewAdapter.ITEM_LEFT_PRIMARY ||
                viewType == ConversationListViewAdapter.ITEM_LEFT_SECONDARY) {
            textView.setBackgroundResource(b ? android.R.color.holo_blue_dark : R.color.primary);
        } else {
            textView.setBackgroundResource(b ? android.R.color.holo_blue_dark : android.R.color.white);
        }
    }
}
