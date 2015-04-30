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
