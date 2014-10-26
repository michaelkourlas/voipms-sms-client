/// <reference path="_references.ts" />

/**
 * Represents an interface for accessing conversation information.
 */
class Conversations {
    /**
     * The local storage key for conversations.
     */
    private static CONVERSATIONS_KEY: string = "conversations";
    /**
     * The local storage key for the index of the conversation being displayed.
     */
    private static ACTIVE_CONVERSATION_INDEX_KEY: string = "activeConversationIndex";

    /**
     * Gets the SMS conversations in local storage.
     * @returns {*} An array of conversations.
     */
    static getConversations(): Conversation[] {
        try {
            var conversationsJson = JSON.parse(window.localStorage.getItem(Conversations.CONVERSATIONS_KEY));
            var conversations: Conversation[] = [];
            for (var i = 0; i < conversationsJson.length; i++) {
                conversations[i] = Conversation.createConversation(conversationsJson[i]);
            }
            return conversations;
        }
        catch (err) {
            return [];
        }
    }

    static setConversations(conversations: Conversation[]) {
        window.localStorage.setItem(Conversations.CONVERSATIONS_KEY, JSON.stringify(conversations));
    }


    private static hash(str) {
        var hash = 5381;
        for (i = 0; i < str.length; i++) {
            char = str.charCodeAt(i);
            hash = ((hash << 5) + hash) + char; /* hash * 33 + c */
        }
        return hash;
    }

    /**
     * Refreshes the SMS conversations through a call to the VoIP.ms API. If the conversations could be successfully
     * retrieved, then the local storage is updated.
     * @param callback A callback function with a single error argument; if the error is null, then the conversations
     * were successfully refreshed.
     */
    static refreshConversations(callback: (err: string) => void): void {
        Api.getConversations(Settings.getUsername(), Settings.getPassword(), Settings.getMessagesHistory(),
            Settings.getLocalPhoneNumber(), function(conversations: Conversation[], err: string) {
                if (conversations === null) {
                    callback(err);
                }
                else {
                    var oldConversations: Conversation[] = Conversations.getConversations();
                    async.eachSeries(conversations, function(conversation, asyncCallback: () => void) {
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

                                if (notificationString !== "" &&
                                    !oldConversation.messages[oldConversation.messages.length - 1].unread) {
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
                                Phone.getContact(null, remotePhoneNumber,
                                    function(contact) {
                                        MainInterface.hideStatusBarNotification(parseInt(
                                            Conversations.hash(remotePhoneNumber)));
                                        if (contact !== null) {
                                            MainInterface.showStatusBarNotification(parseInt(
                                                    Conversations.hash(remotePhoneNumber)), contact.displayName,
                                                message, badgeCount, JSON.stringify(remotePhoneNumber));
                                            asyncCallback();
                                        }
                                        else {
                                            MainInterface.showStatusBarNotification(parseInt(
                                                    Conversations.hash(remotePhoneNumber)), remotePhoneNumber, message,
                                                badgeCount, JSON.stringify(remotePhoneNumber));
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
                        Conversations.setConversations(conversations);
                        callback(null);
                    });
                }
            }
        );
    }

    /**
     * Gets the active conversation index.
     * @returns {number} The active conversation index.
     */
    public static getActiveConversationIndex(): number {
        return JSON.parse(window.localStorage.getItem(Conversations.ACTIVE_CONVERSATION_INDEX_KEY));
    }

    /**
     * Sets the active conversation index.
     * @param index The index of the active conversation within the conversations array.
     */
    public static setActiveConversationIndex(index: number) {
        window.localStorage.setItem(Conversations.ACTIVE_CONVERSATION_INDEX_KEY, JSON.stringify(index));
    }
}