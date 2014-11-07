/// <reference path="_references.ts" />

/**
 * Interface to retrieve and manipulate conversations.
 */
module conversations {
    /**
     * The local storage key for conversations.
     */
    var CONVERSATIONS_KEY: string = "conversations";
    /**
     * The local storage key for the phone number associated with the conversation being displayed.
     */
    var ACTIVE_CONVERSATION_PHONE_NUMBER_KEY: string = "activeConversationPhoneNumber";

    /**
     * Gets the SMS conversations in local storage.
     * @returns {*} An array of conversations.
     */
    export function get(): Conversation[] {
        try {
            var conversationsJson = JSON.parse(window.localStorage.getItem(CONVERSATIONS_KEY));
            var conversationsData: Conversation[] = [];
            for (var i = 0; i < conversationsJson.length; i++) {
                conversationsData[i] = Conversation.createConversation(conversationsJson[i]);
            }
            return conversationsData;
        }
        catch (err) {
            return [];
        }
    }

    /**
     * Sets the SMS conversations in local storage.
     * @param conversations The conversations to store.
     */
    export function set(conversations: Conversation[]) {
        window.localStorage.setItem(CONVERSATIONS_KEY, JSON.stringify(conversations));
    }

    /**
     * Gets the active conversation phone number.
     * @returns {number} The active conversation phone number.
     */
    export function getActiveConversationPhoneNumber(): string {
        return JSON.parse(window.localStorage.getItem(ACTIVE_CONVERSATION_PHONE_NUMBER_KEY));
    }

    /**
     * Sets the active conversation phone number.
     * @param index The active conversation phone number.
     */
    export function setActiveConversationPhoneNumber(index: string) {
        window.localStorage.setItem(ACTIVE_CONVERSATION_PHONE_NUMBER_KEY, JSON.stringify(index));
    }

    /**
     * Refreshes the SMS conversations through a call to the VoIP.ms API. If the conversations could be successfully
     * retrieved, then the local storage is updated.
     * @param callback A callback function with a single error argument; if the error is null, then the conversations
     * were successfully refreshed.
     */
    export function refresh(callback: (err: string) => void): void {
        api.getConversations(settings.getUsername(), settings.getPassword(), settings.getMessagesHistory(),
            settings.getLocalPhoneNumber(), function(conversationsData: Conversation[], err: string) {
                if (conversationsData === null) {
                    callback(err);
                }
                else {
                    var oldConversations: Conversation[] = conversations.get();
                    async.eachSeries(conversationsData, function(conversation, asyncCallback: () => void) {
                        var oldConversation: Conversation = null;
                        var noChange: boolean = false;

                        for (var i = 0; i < oldConversations.length; i++) {
                            if (conversation.getRemotePhoneNumber() ===
                                oldConversations[i].getRemotePhoneNumber()) {
                                if (conversation.equals(oldConversations[i])) {
                                    noChange = true;
                                }
                                else {
                                    oldConversation = oldConversations[i];
                                }
                                break;
                            }
                        }

                        if (!noChange) {
                            // New message(s)
                            if (oldConversation !== null) {
                                var mostRecentReadMessage: Message = null;
                                for (var i = oldConversation.messages.length - 1; i >= 0; i--) {
                                    if (!oldConversation.messages[i].unread &&
                                        oldConversation.messages[i].type === MessageType.Incoming) {
                                        mostRecentReadMessage = oldConversation.messages[i];
                                        break;
                                    }
                                }

                                var notificationString: string = "";
                                var badgeCount: number = 0;
                                for (var i = conversation.messages.length - 1; i >= 0; i--) {
                                    if ((mostRecentReadMessage === null ||
                                        mostRecentReadMessage.date.isBefore(conversation.messages[i].date)) &&
                                        conversation.messages[i].type === MessageType.Incoming) {
                                        conversation.messages[i].unread = true;
                                        notificationString = conversation.messages[i].text + "\n" + notificationString;
                                        badgeCount++;
                                    }
                                    else {
                                        break;
                                    }
                                }
                                notificationString = notificationString.trim();

                                if (notificationString !== "" && !oldConversation.messages[oldConversation.messages.length - 1].unread) {
                                    showNotification(notificationString, conversation.getRemotePhoneNumber(),
                                        badgeCount);
                                }
                                else {
                                    asyncCallback();
                                }
                            }
                            // New conversation
                            else {
                                var notificationString: string = "";
                                var badgeCount: number = 0;
                                for (var i = conversation.messages.length - 1; i >= 0; i--) {
                                    if (conversation.messages[i].type === MessageType.Incoming) {
                                        conversation.messages[i].unread = true;
                                        notificationString = conversation.messages[i].text + "\n" + notificationString;
                                        badgeCount++;
                                    }
                                    else {
                                        break;
                                    }
                                }
                                notificationString = notificationString.trim();

                                if (notificationString !== "") {
                                    showNotification(notificationString, conversation.getRemotePhoneNumber(),
                                        badgeCount);
                                }
                                else {
                                    asyncCallback();
                                }
                            }

                            function showNotification(message: string, remotePhoneNumber: string, badgeCount: number) {
                                contacts.getContact(null, remotePhoneNumber,
                                    function(contact) {
                                        ui.notifications.hideStatusBarNotification(hash(remotePhoneNumber));
                                        if (contact !== null) {
                                            ui.notifications.showStatusBarNotification(
                                                hash(remotePhoneNumber), contact.displayName, message, badgeCount,
                                                JSON.stringify(remotePhoneNumber));
                                            asyncCallback();
                                        }
                                        else {
                                            ui.notifications.showStatusBarNotification(
                                                hash(remotePhoneNumber), remotePhoneNumber, message, badgeCount,
                                                JSON.stringify(remotePhoneNumber));
                                            asyncCallback();
                                        }
                                    }
                                );
                            }
                        }
                        else {
                            asyncCallback();
                        }
                    }, function() {
                        conversations.set(conversationsData);
                        callback(null);
                    });
                }
            }
        );
    }

    /**
     * Marks all of the conversations as read.
     */
    export function markAllAsRead() {
        var conversationsData = conversations.get();
        for (var i = 0; i < conversationsData.length; i++) {
            conversationsData[i].markAllMessagesAsRead();
        }
        conversations.set(conversationsData);
    }

    /**
     * Hashes a string.
     * @param str The string to hash.
     * @returns {number} The hash.
     */
    function hash(str) {
        var hash = 5381;
        for (var i = 0; i < str.length; i++) {
            var character = str.charCodeAt(i);
            hash = ((hash << 5) + hash) + character;
        }
        return hash;
    }
}