/// <reference path="_references.ts" />
/**
 * Interface to retrieve and manipulate conversations.
 */
var conversations;
(function (_conversations) {
    /**
     * The local storage key for conversations.
     */
    var CONVERSATIONS_KEY = "conversations";
    /**
     * The local storage key for the phone number associated with the conversation being displayed.
     */
    var ACTIVE_CONVERSATION_PHONE_NUMBER_KEY = "activeConversationPhoneNumber";
    /**
     * Gets the SMS conversations in local storage.
     * @returns {*} An array of conversations.
     */
    function get() {
        try {
            var conversationsJson = JSON.parse(window.localStorage.getItem(CONVERSATIONS_KEY));
            var conversationsData = [];
            for (var i = 0; i < conversationsJson.length; i++) {
                conversationsData[i] = Conversation.createConversation(conversationsJson[i]);
            }
            return conversationsData;
        }
        catch (err) {
            return [];
        }
    }
    _conversations.get = get;
    /**
     * Sets the SMS conversations in local storage.
     * @param conversations The conversations to store.
     */
    function set(conversations) {
        window.localStorage.setItem(CONVERSATIONS_KEY, JSON.stringify(conversations));
    }
    _conversations.set = set;
    /**
     * Gets the active conversation phone number.
     * @returns {number} The active conversation phone number.
     */
    function getActiveConversationPhoneNumber() {
        return JSON.parse(window.localStorage.getItem(ACTIVE_CONVERSATION_PHONE_NUMBER_KEY));
    }
    _conversations.getActiveConversationPhoneNumber = getActiveConversationPhoneNumber;
    /**
     * Sets the active conversation phone number.
     * @param index The active conversation phone number.
     */
    function setActiveConversationPhoneNumber(index) {
        window.localStorage.setItem(ACTIVE_CONVERSATION_PHONE_NUMBER_KEY, JSON.stringify(index));
    }
    _conversations.setActiveConversationPhoneNumber = setActiveConversationPhoneNumber;
    /**
     * Refreshes the SMS conversations through a call to the VoIP.ms API. If the conversations could be successfully
     * retrieved, then the local storage is updated.
     * @param callback A callback function with a single error argument; if the error is null, then the conversations
     * were successfully refreshed.
     */
    function refresh(callback) {
        api.getConversations(settings.getUsername(), settings.getPassword(), settings.getMessagesHistory(), settings.getLocalPhoneNumber(), function (conversationsData, err) {
            if (conversationsData === null) {
                callback(err);
            }
            else {
                var oldConversations = conversations.get();
                async.eachSeries(conversationsData, function (conversation, asyncCallback) {
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
                            contacts.getContact(null, remotePhoneNumber, function (contact) {
                                ui.notifications.hideStatusBarNotification(hash(remotePhoneNumber));
                                if (contact !== null) {
                                    ui.notifications.showStatusBarNotification(hash(remotePhoneNumber), contact.displayName, message, badgeCount, JSON.stringify(remotePhoneNumber));
                                    asyncCallback();
                                }
                                else {
                                    ui.notifications.showStatusBarNotification(hash(remotePhoneNumber), remotePhoneNumber, message, badgeCount, JSON.stringify(remotePhoneNumber));
                                    asyncCallback();
                                }
                            });
                        }
                    }
                    else {
                        asyncCallback();
                    }
                }, function () {
                    conversations.set(conversationsData);
                    callback(null);
                });
            }
        });
    }
    _conversations.refresh = refresh;
    /**
     * Marks all of the conversations as read.
     */
    function markAllAsRead() {
        var conversationsData = conversations.get();
        for (var i = 0; i < conversationsData.length; i++) {
            conversationsData[i].markAllMessagesAsRead();
        }
        conversations.set(conversationsData);
    }
    _conversations.markAllAsRead = markAllAsRead;
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
})(conversations || (conversations = {}));
//# sourceMappingURL=conversations.js.map