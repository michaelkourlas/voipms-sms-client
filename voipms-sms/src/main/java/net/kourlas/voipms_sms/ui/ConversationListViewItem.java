/*
 * VoIP.ms SMS
 * Copyright (C) 2015 Michael Kourlas and other contributors
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
        TextView textView = (TextView) this.findViewById(R.id.message);
        if (viewType == ConversationListViewAdapter.ITEM_LEFT_PRIMARY ||
                viewType == ConversationListViewAdapter.ITEM_LEFT_SECONDARY) {
            textView.setBackgroundResource(b ? android.R.color.holo_blue_dark : R.color.primary);
        } else {
            textView.setBackgroundResource(b ? android.R.color.holo_blue_dark : android.R.color.white);
        }
    }
}
