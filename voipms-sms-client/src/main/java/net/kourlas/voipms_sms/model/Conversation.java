/*
 * VoIP.ms SMS
 * Copyright (C) 2015 Michael Kourlas
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

package net.kourlas.voipms_sms.model;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Conversation implements Comparable<Conversation> {
    private final List<Sms> smses;

    public Conversation(Sms[] smses) {
        this.smses = new ArrayList<Sms>();
        this.smses.addAll(Arrays.asList(smses));
        Collections.sort(this.smses);
    }

    public Sms getMostRecentSms() {
        return smses.get(0);
    }

    public Sms[] getAllSms() {
        Sms[] smsArray = new Sms[smses.size()];
        return smses.toArray(smsArray);
    }

    public void addSms(Sms sms) {
        smses.add(sms);
        Collections.sort(smses);
    }

    public String getContact() {
        return smses.get(0).getContact();
    }

    public boolean isUnread() {
        return smses.get(0).isUnread();
    }

    @Override
    public int compareTo(@NonNull Conversation another) {
        return smses.get(0).compareTo(another.smses.get(0));
    }
}
