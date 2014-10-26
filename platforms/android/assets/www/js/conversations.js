/// <reference path="_references.ts" />
/**
 * Represents an interface for accessing conversation information.
 */
var Conversations = (function () {
    function Conversations() {
    }
    /**
     * Gets the SMS conversations in local storage.
     * @returns {*} An array of conversations.
     */
    Conversations.getConversations = function () {
        try {
            var conversationsJson = JSON.parse(window.localStorage.getItem(Conversations.CONVERSATIONS_KEY));
            var conversations = [];
            for (var i = 0; i < conversationsJson.length; i++) {
                conversations[i] = Conversation.createConversation(conversationsJson[i]);
            }
            return conversations;
        }
        catch (err) {
            return [];
        }
    };
    Conversations.setConversations = function (conversations) {
        window.localStorage.setItem(Conversations.CONVERSATIONS_KEY, JSON.stringify(conversations));
    };
    Conversations.hash = function (str) {
        var hash = 5381;
        for (i = 0; i < str.length; i++) {
            char = str.charCodeAt(i);
            hash = ((hash << 5) + hash) + char; /* hash * 33 + c */
        }
        return hash;
    };
    /**
     * Refreshes the SMS conversations through a call to the VoIP.ms API. If the conversations could be successfully
     * retrieved, then the local storage is updated.
     * @param callback A callback function with a single error argument; if the error is null, then the conversations
     * were successfully refreshed.
     */
    Conversations.refreshConversations = function (callback) {
        Api.getConversations(Settings.getUsername(), Settings.getPassword(), Settings.getMessagesHistory(), Settings.getLocalPhoneNumber(), function (conversations, err) {
            if (conversations === null) {
                callback(err);
            }
            else {
                var oldConversations = Conversations.getConversations();
                async.eachSeries(conversations, function (conversation, asyncCallback) {
                    var oldConversation = null;
                    var noChange = false;
                    for (var i = 0; i < oldConversations.length; i++) {
                        if (conversation.getRemotePhoneNumber() === oldConversations[i].getRemotePhoneNumber()) {
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
                            var mostRecentReadMessage = null;
                            for (var i = oldConversation.messages.length - 1; i >= 0; i--) {
                                if (!oldConversation.messages[i].unread && oldConversation.messages[i].type === 0 /* Incoming */) {
                                    mostRecentReadMessage = oldConversation.messages[i];
                                    break;
                                }
                            }
                            var notificationString = "";
                            var badgeCount = 0;
                            for (var i = conversation.messages.length - 1; i >= 0; i--) {
                                if ((mostRecentReadMessage === null || mostRecentReadMessage.date.isBefore(conversation.messages[i].date)) && conversation.messages[i].type === 0 /* Incoming */) {
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
                                showNotification(notificationString, conversation.getRemotePhoneNumber(), badgeCount);
                            }
                            else {
                                asyncCallback();
                            }
                        }
                        else {
                            var notificationString = "";
                            var badgeCount = 0;
                            for (var i = conversation.messages.length - 1; i >= 0; i--) {
                                if (conversation.messages[i].type === 0 /* Incoming */) {
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
                                showNotification(notificationString, conversation.getRemotePhoneNumber(), badgeCount);
                            }
                            else {
                                asyncCallback();
                            }
                        }
                        function showNotification(message, remotePhoneNumber, badgeCount) {
                            Phone.getContact(null, remotePhoneNumber, function (contact) {
                                MainInterface.hideStatusBarNotification(parseInt(Conversations.hash(remotePhoneNumber)));
                                if (contact !== null) {
                                    MainInterface.showStatusBarNotification(parseInt(Conversations.hash(remotePhoneNumber)), contact.displayName, message, badgeCount, JSON.stringify(remotePhoneNumber));
                                    asyncCallback();
                                }
                                else {
                                    MainInterface.showStatusBarNotification(parseInt(Conversations.hash(remotePhoneNumber)), remotePhoneNumber, message, badgeCount, JSON.stringify(remotePhoneNumber));
                                    asyncCallback();
                                }
                            });
                        }
                    }
                    else {
                        asyncCallback();
                    }
                }, function () {
                    Conversations.setConversations(conversations);
                    callback(null);
                });
            }
        });
    };
    /**
     * Gets the active conversation index.
     * @returns {number} The active conversation index.
     */
    Conversations.getActiveConversationIndex = function () {
        return JSON.parse(window.localStorage.getItem(Conversations.ACTIVE_CONVERSATION_INDEX_KEY));
    };
    /**
     * Sets the active conversation index.
     * @param index The index of the active conversation within the conversations array.
     */
    Conversations.setActiveConversationIndex = function (index) {
        window.localStorage.setItem(Conversations.ACTIVE_CONVERSATION_INDEX_KEY, JSON.stringify(index));
    };
    /**
     * The local storage key for conversations.
     */
    Conversations.CONVERSATIONS_KEY = "conversations";
    /**
     * The local storage key for the index of the conversation being displayed.
     */
    Conversations.ACTIVE_CONVERSATION_INDEX_KEY = "activeConversationIndex";
    return Conversations;
})();
//# sourceMappingURL=Conversations.js.map