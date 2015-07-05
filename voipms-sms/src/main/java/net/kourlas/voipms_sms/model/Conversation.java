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

package net.kourlas.voipms_sms.model;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Conversation implements Comparable<Conversation> {
    private final List<Message> messages;

    public Conversation(Message[] messages) {
        this.messages = new ArrayList<>();
        this.messages.addAll(Arrays.asList(messages));
        Collections.sort(this.messages);
    }

    public Message getMostRecentSms() {
        return messages.get(0);
    }

    public Message[] getMessages() {
        Message[] messageArray = new Message[messages.size()];
        return messages.toArray(messageArray);
    }

    public void addSms(Message message) {
        messages.add(message);
        Collections.sort(messages);
    }

    public String getContact() {
        return messages.get(0).getContact();
    }

    public String getDid() {
        return messages.get(0).getDid();
    }

    public boolean isUnread() {
        return messages.get(0).isUnread();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Conversation) {
            if (this == o) {
                return true;
            }

            Conversation other = (Conversation) o;
            if (messages.size() != other.messages.size()) {
                return false;
            }

            for (int i = 0; i < messages.size(); i++) {
                if (!messages.get(i).equals(other.messages.get(i))) {
                    return false;
                }
            }

            return true;
        }
        else {
            return super.equals(o);
        }
    }

    @Override
    public int compareTo(@NonNull Conversation another) {
        return messages.get(0).compareTo(another.messages.get(0));
    }
}
