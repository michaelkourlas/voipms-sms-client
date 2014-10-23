/// <reference path="../third-party/async/async.d.ts" />
/// <reference path="../third-party/jquery/jquery.d.ts" />
/// <reference path="conversation.ts" />
/// <reference path="settings.ts" />
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
            return null;
        }
    };
    /**
     * Refreshes the SMS conversations through a call to the VoIP.ms API. If the conversations could be successfully
     * retrieved, then the local storage is updated.
     * @param callback A callback function with a single error argument; if the error is null, then the conversations
     * were successfully refreshed.
     */
    Conversations.refreshConversations = function (callback) {
        Conversations.getApiConversations(Settings.getUsername(), Settings.getPassword(), function (conversations, err) {
            if (conversations === null) {
                callback(err);
            }
            else {
                var oldConversations = Conversations.getConversations();
                for (var i = 0; i < conversations.length; i++) {
                    var match = false;
                    var conversation = null;
                    for (var j = 0; j < oldConversations.length; j++) {
                        if (conversations[i].equals(oldConversations[j])) {
                            match = true;
                            conversation = oldConversations[j];
                            break;
                        }
                    }
                    if (!match) {
                        // New message (or message removed)
                        if (conversations[i].getRemotePhoneNumber() === oldConversations[j].getRemotePhoneNumber()) {
                        }
                        else {
                        }
                    }
                }
                window.plugin.notification.local.add({
                    title: "Title",
                    message: "Message",
                    autoCancel: true
                });
                window.localStorage.setItem(Conversations.CONVERSATIONS_KEY, JSON.stringify(conversations));
                callback(null);
            }
        });
    };
    /**
     * Gets SMS conversations from the VoIP.ms API.
     * @param username The API username.
     * @param password The API password.
     * @param callback A callback function with a conversation array argument and error argument.
     */
    Conversations.getApiConversations = function (username, password, callback) {
        // Continue making API requests until limit exceeds number of messages returned
        var limitArr = [Conversations.DEFAULT_LIMIT];
        async.eachSeries(limitArr, function (limit, limitCallback) {
            var url = Conversations.createApiUrl(username, password, limit);
            var request = $.getJSON(url);
            request.done(function (data) {
                var conversations = Conversations.parseApiRequest(data);
                if (conversations != null) {
                    var messageCount = 0;
                    for (var i = 0; i < conversations.length; i++) {
                        messageCount += conversations[i].messages.length;
                    }
                    if (messageCount >= limit) {
                        limitArr.push(limit + Conversations.LIMIT_INCREMENT);
                    }
                    else {
                        callback(conversations, null);
                    }
                }
                else {
                    callback(null, "Error decoding VoIP.ms API response. Are your username and password correct?");
                }
                limitCallback(null, null);
            });
            request.fail(function () {
                callback(null, "Error accessing VoIP.ms API. Are you connected to the Internet?");
                limitCallback(null, null);
            });
        }, function () {
        });
    };
    /**
     * Creates an API URL for retrieving SMS conversations.
     * @param username The API username.
     * @param password The API password.
     * @param limit The total number of SMS conversations to retrieve.
     * @returns {string} The API URL for retrieving SMS conversations.
     */
    Conversations.createApiUrl = function (username, password, limit) {
        var startDate = moment().utc().subtract(Settings.getHistory(), "day");
        var endDate = moment().utc();
        var voipUrl = "https://www.voip.ms/api/v1/rest.php?" + "&" + "api_username=" + encodeURIComponent(username) + "&" + "api_password=" + encodeURIComponent(password) + "&" + "method=getSMS" + "&" + "limit=" + encodeURIComponent(String(limit)) + "&" + "from=" + encodeURIComponent(startDate.toISOString().substr(0, 10)) + "&" + "to=" + encodeURIComponent(endDate.toISOString().substr(0, 10));
        var yqlCommand = "select * from json where url=\"" + voipUrl + "\"";
        var yqlUrl = "https://query.yahooapis.com/v1/public/yql?" + "q=" + encodeURIComponent(yqlCommand) + "&" + "format=json" + "&" + "callback=?";
        return yqlUrl;
    };
    /**
     * Parses the API request into an array of conversations.
     * @param data Raw data from the VoIP.ms API.
     * @returns {*} An array of conversations.
     */
    Conversations.parseApiRequest = function (data) {
        try {
            var conversations = [];
            var rawMessages = data["query"]["results"]["json"]["sms"];
            if (!(rawMessages instanceof Array)) {
                rawMessages = [rawMessages];
            }
            for (var i = rawMessages.length - 1; i >= 0; i--) {
                var conversation = null;
                for (var j = 0; j < conversations.length; j++) {
                    if (conversations[j].getRemotePhoneNumber() === rawMessages[i]["contact"]) {
                        conversation = conversations[j];
                    }
                }
                if (conversation === null) {
                    conversation = new Conversation();
                    conversations.push(conversation);
                }
                var message = new Message(parseInt(rawMessages[i]["id"]), rawMessages[i]["message"], moment.utc(rawMessages[i]["date"]), parseInt(rawMessages[i]["type"]) === 0 ? 1 /* Outgoing */ : 0 /* Incoming */, rawMessages[i]["did"], rawMessages[i]["contact"]);
                conversation.messages.push(message);
            }
            conversations.sort(function (a, b) {
                if (a.getEndDate().isSame(b.getEndDate())) {
                    return 0;
                }
                else if (a.getEndDate().isBefore(b.getEndDate())) {
                    return 1;
                }
                else {
                    return -1;
                }
            });
            return conversations;
        }
        catch (err) {
            return null;
        }
    };
    /**
     * The local storage key for the username setting.
     */
    Conversations.CONVERSATIONS_KEY = "conversations";
    /**
     * The initial limit for an SMS API request.
     */
    Conversations.DEFAULT_LIMIT = 10000;
    /**
     * The limit increment for an SMS API request.
     */
    Conversations.LIMIT_INCREMENT = 1000;
    return Conversations;
})();
//# sourceMappingURL=conversations.js.map