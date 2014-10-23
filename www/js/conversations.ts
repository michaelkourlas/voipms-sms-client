/// <reference path="../third-party/async/async.d.ts" />
/// <reference path="../third-party/jquery/jquery.d.ts" />
/// <reference path="conversation.ts" />
/// <reference path="settings.ts" />

class Conversations {
    /**
     * The local storage key for the username setting.
     */
    private static CONVERSATIONS_KEY: string = "conversations";
    /**
     * The initial limit for an SMS API request.
     */
    private static DEFAULT_LIMIT: number = 10000;
    /**
     * The limit increment for an SMS API request.
     */
    private static LIMIT_INCREMENT: number = 1000;

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
            return null;
        }
    }

    /**
     * Refreshes the SMS conversations through a call to the VoIP.ms API. If the conversations could be successfully
     * retrieved, then the local storage is updated.
     * @param callback A callback function with a single error argument; if the error is null, then the conversations
     * were successfully refreshed.
     */
    static refreshConversations(callback: (err: string) => void): void {
        Conversations.getApiConversations(Settings.getUsername(), Settings.getPassword(),
            function(conversations: Conversation[], err: string) {
                if (conversations === null) {
                    callback(err);
                }
                else {
                    var oldConversations: Conversation[] = Conversations.getConversations();
                    for (var i = 0; i < conversations.length; i++) {
                        var match: boolean = false;
                        var conversation: Conversation = null;
                        for (var j = 0; j < oldConversations.length; j++) {
                            if (conversations[i].equals(oldConversations[j])) {
                                match = true;
                                conversation = oldConversations[j];
                                break;
                            }
                        }
                        if (!match) {
                            // New message (or message removed)
                            if (conversations[i].getRemotePhoneNumber() ===
                                oldConversations[j].getRemotePhoneNumber()) {

                            }
                            // New conversation
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
            }
        );
    }

    /**
     * Gets SMS conversations from the VoIP.ms API.
     * @param username The API username.
     * @param password The API password.
     * @param callback A callback function with a conversation array argument and error argument.
     */
    private static getApiConversations(username: string, password: string,
                                       callback: (conversations: Conversation[], err: string) => void): void {
        // Continue making API requests until limit exceeds number of messages returned
        var limitArr: number[] = [Conversations.DEFAULT_LIMIT];
        async.eachSeries(limitArr, function(limit, limitCallback) {
            var url: string = Conversations.createApiUrl(username, password, limit);
            var request = $.getJSON(url);
            request.done(function(data : Object) {
                var conversations: Conversation[] = Conversations.parseApiRequest(data);
                if (conversations != null) {
                    var messageCount: number = 0;
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
            request.fail(function() {
                callback(null, "Error accessing VoIP.ms API. Are you connected to the Internet?");
                limitCallback(null, null);
            });
        }, function() {

        });
    }

    /**
     * Creates an API URL for retrieving SMS conversations.
     * @param username The API username.
     * @param password The API password.
     * @param limit The total number of SMS conversations to retrieve.
     * @returns {string} The API URL for retrieving SMS conversations.
     */
    private static createApiUrl(username: string, password: string, limit: number): string {
        var startDate: Moment = moment().utc().subtract(Settings.getHistory(), "day");
        var endDate: Moment = moment().utc();

        var voipUrl = "https://www.voip.ms/api/v1/rest.php?" + "&" +
            "api_username=" + encodeURIComponent(username) + "&" +
            "api_password=" + encodeURIComponent(password) + "&" +
            "method=getSMS" + "&" +
            "limit=" + encodeURIComponent(String(limit)) + "&" +
            "from=" + encodeURIComponent(startDate.toISOString().substr(0, 10)) + "&" +
            "to=" + encodeURIComponent(endDate.toISOString().substr(0, 10));
        var yqlCommand = "select * from json where url=\"" + voipUrl + "\"";
        var yqlUrl = "https://query.yahooapis.com/v1/public/yql?" +
            "q=" + encodeURIComponent(yqlCommand) + "&" +
            "format=json" + "&" +
            "callback=?";

        return yqlUrl;
    }

    /**
     * Parses the API request into an array of conversations.
     * @param data Raw data from the VoIP.ms API.
     * @returns {*} An array of conversations.
     */
    private static parseApiRequest(data: Object): Conversation[]  {
        try {
            var conversations: Conversation[] = [];
            var rawMessages = data["query"]["results"]["json"]["sms"];

            if (!(rawMessages instanceof Array)) {
                rawMessages = [rawMessages];
            }

            for (var i = rawMessages.length - 1; i >= 0; i--) {
                var conversation : Conversation = null;
                for (var j = 0; j < conversations.length; j++) {
                    if (conversations[j].getRemotePhoneNumber() === rawMessages[i]["contact"]) {
                        conversation = conversations[j];
                    }
                }
                if (conversation === null) {
                    conversation = new Conversation();
                    conversations.push(conversation);
                }

                var message : Message = new Message(parseInt(rawMessages[i]["id"]), rawMessages[i]["message"],
                    moment.utc(rawMessages[i]["date"]), parseInt(rawMessages[i]["type"]) === 0 ? MessageType.Outgoing :
                        MessageType.Incoming, rawMessages[i]["did"], rawMessages[i]["contact"]);
                conversation.messages.push(message);
            }

            conversations.sort(function(a: Conversation, b: Conversation): number {
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
    }
}