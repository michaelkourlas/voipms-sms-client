/// <reference path="_references.ts" />
/**
 * Interface to access the VoIP.ms API.
 */
var api;
(function (api) {
    /**
     * Default SMS retrieval limit.
     * @type {number}
     */
    var DEFAULT_LIMIT = 10000;
    /**
     * Increment for SMS retrieval limit increases.
     * @type {number}
     */
    var LIMIT_INCREMENT = 1000;
    /**
     * Gets a list of conversations from the VoIP.ms API.
     * @param username The API username.
     * @param password The API password.
     * @param history The number of days of history to retrieve.
     * @param localPhoneNumber The local phone number.
     * @param callback A callback function returning the conversations and an error string. The conversations variable
     * will be null if there is an error, while the error variable will be null if there is no error.
     */
    function getConversations(username, password, history, localPhoneNumber, callback) {
        // Continue making API requests until limit exceeds number of messages returned
        var limitArr = [DEFAULT_LIMIT];
        async.eachSeries(limitArr, function (limit, limitCallback) {
            var url = api.createGetConversationsApiUrl(username, password, limit, history, localPhoneNumber);
            var request = $.getJSON(url);
            request.done(function (data) {
                var conversations = api.parseGetConversationsApiResponse(data);
                if (conversations != null) {
                    var messageCount = 0;
                    for (var i = 0; i < conversations.length; i++) {
                        messageCount += conversations[i].messages.length;
                    }
                    if (messageCount >= limit) {
                        limitArr.push(limit + LIMIT_INCREMENT);
                    }
                    else {
                        callback(conversations, null);
                    }
                }
                else {
                    callback(null, "Error decoding VoIP.ms API response. Are your username, password, and selected " + "phone number correct?");
                }
                limitCallback(null, null);
            });
            request.fail(function () {
                callback(null, "Error accessing VoIP.ms API. Are you connected to the Internet?");
                limitCallback(null, null);
            });
        }, function () {
        });
    }
    api.getConversations = getConversations;
    /**
     * Creates an API URL for the getSMS method.
     * @param username The API username.
     * @param password The API password.
     * @param limit The total number of messages to retrieve.
     * @param history The number of days of history to retrieve.
     * @param localPhoneNumber The local phone number.
     * @returns {string} The URL for the getSMS method.
     */
    function createGetConversationsApiUrl(username, password, limit, history, localPhoneNumber) {
        var startDate = moment().utc().subtract(history, "day");
        var endDate = moment().utc();
        var voipUrl = "https://www.voip.ms/api/v1/rest.php?" + "&" + "api_username=" + encodeURIComponent(username) + "&" + "api_password=" + encodeURIComponent(password) + "&" + "method=getSMS" + "&" + "did=" + encodeURIComponent(localPhoneNumber) + "&" + "limit=" + encodeURIComponent(String(limit)) + "&" + "from=" + encodeURIComponent(startDate.toISOString().substr(0, 10)) + "&" + "to=" + encodeURIComponent(endDate.toISOString().substr(0, 10));
        var yqlCommand = "select * from json where url=\"" + voipUrl + "\"";
        return "https://query.yahooapis.com/v1/public/yql?" + "q=" + encodeURIComponent(yqlCommand) + "&" + "format=json" + "&" + "callback=?";
    }
    api.createGetConversationsApiUrl = createGetConversationsApiUrl;
    /**
     * Parses the getSMS API response.
     * @param data The API response.
     * @returns {*} A list of conversations.
     */
    function parseGetConversationsApiResponse(data) {
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
                var message = new Message(parseInt(rawMessages[i]["id"]), rawMessages[i]["message"], moment(rawMessages[i]["date"]), parseInt(rawMessages[i]["type"]) === 0 ? 1 /* Outgoing */ : 0 /* Incoming */, rawMessages[i]["did"], rawMessages[i]["contact"]);
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
    }
    api.parseGetConversationsApiResponse = parseGetConversationsApiResponse;
    /**
     * Gets a list of local phone numbers attached to a VoIP.ms account.
     * @param username The API username.
     * @param password The API password.
     * @param callback A callback function returning the phone numbers and an error string. The conversations variable
     * will be null if there is an error, while the error variable will be null if there is no error.
     */
    function getLocalPhoneNumbers(username, password, callback) {
        var url = api.createGetLocalPhoneNumbersApiUrl(username, password);
        var request = $.getJSON(url);
        request.done(function (data) {
            var phoneNumbers = api.parseGetLocalPhoneNumbersApiResponse(data);
            if (phoneNumbers != null) {
                callback(phoneNumbers, null);
            }
            else {
                callback(null, "Error decoding VoIP.ms API response. Are your username, password, and selected phone " + "number correct?");
            }
        });
        request.fail(function () {
            callback(null, "Error accessing VoIP.ms API. Are you connected to the Internet?");
        });
    }
    api.getLocalPhoneNumbers = getLocalPhoneNumbers;
    /**
     * Creates an API URL for the getDIDsInfo method.
     * @param username The API username.
     * @param password The API password.
     * @returns {string} The URL for the getDIDsInfo method.
     */
    function createGetLocalPhoneNumbersApiUrl(username, password) {
        var voipUrl = "https://www.voip.ms/api/v1/rest.php?" + "&" + "api_username=" + encodeURIComponent(username) + "&" + "api_password=" + encodeURIComponent(password) + "&" + "method=getDIDsInfo";
        var yqlCommand = "select * from json where url=\"" + voipUrl + "\"";
        return "https://query.yahooapis.com/v1/public/yql?" + "q=" + encodeURIComponent(yqlCommand) + "&" + "format=json" + "&" + "callback=?";
    }
    api.createGetLocalPhoneNumbersApiUrl = createGetLocalPhoneNumbersApiUrl;
    /**
     * Parses the getDIDsInfo API response.
     * @param data The API response.
     * @returns {*} The phone numbers.
     */
    function parseGetLocalPhoneNumbersApiResponse(data) {
        try {
            var phoneNumbers = [];
            var rawPhoneNumbers = data["query"]["results"]["json"]["dids"];
            if (!(rawPhoneNumbers instanceof Array)) {
                rawPhoneNumbers = [rawPhoneNumbers];
            }
            for (var i = 0; i < rawPhoneNumbers.length; i++) {
                if (rawPhoneNumbers[i]["sms_available"]) {
                    phoneNumbers.push(rawPhoneNumbers[i]["did"]);
                }
            }
            return phoneNumbers;
        }
        catch (err) {
            return null;
        }
    }
    api.parseGetLocalPhoneNumbersApiResponse = parseGetLocalPhoneNumbersApiResponse;
    /**
     * Sends an SMS message.
     * @param username The API username.
     * @param password The API password.
     * @param localPhoneNumber The source phone number.
     * @param remotePhoneNumber The destination phone number.
     * @param messageText The text of the message.
     * @param callback A callback function returning whether or not the SMS was successfully sent and an error string.
     * The conversations variable will be null if there is an error, while the error variable will be null if there is
     * no error.
     */
    function sendSms(username, password, localPhoneNumber, remotePhoneNumber, messageText, callback) {
        var url = api.createSendMessageApiUrl(username, password, localPhoneNumber, remotePhoneNumber, messageText);
        var request = $.getJSON(url);
        request.done(function (data) {
            var success = api.parseSendMessageApiResponse(data);
            if (success != null) {
                callback(success, null);
            }
            else {
                callback(null, "Error decoding VoIP.ms API response. Are your username, password, and selected phone " + "number correct?");
            }
        });
        request.fail(function () {
            callback(null, "Error accessing VoIP.ms API. Are you connected to the Internet?");
        });
    }
    api.sendSms = sendSms;
    /**
     * Creates an API URL for the sendSMS method.
     * @param username The API username.
     * @param password The API password.
     * @param localPhoneNumber The local phone number.
     * @param remotePhoneNumber The remote phone number.
     * @param messageText The text of the message.
     * @returns {string} The URL for the sendSMS method.
     */
    function createSendMessageApiUrl(username, password, localPhoneNumber, remotePhoneNumber, messageText) {
        var voipUrl = "https://www.voip.ms/api/v1/rest.php?" + "&" + "api_username=" + encodeURIComponent(username) + "&" + "api_password=" + encodeURIComponent(password) + "&" + "method=sendSMS" + "&" + "did=" + encodeURIComponent(localPhoneNumber) + "&" + "dst=" + encodeURIComponent(remotePhoneNumber) + "&" + "message=" + encodeURIComponent(messageText);
        var yqlCommand = "select * from json where url=\"" + voipUrl + "\"";
        return "https://query.yahooapis.com/v1/public/yql?" + "q=" + encodeURIComponent(yqlCommand) + "&" + "format=json" + "&" + "callback=?";
    }
    api.createSendMessageApiUrl = createSendMessageApiUrl;
    /**
     * Parses the sendSMS API response.
     * @param data The API response.
     * @returns {*} Whether or not the SMS was successfully sent.
     */
    function parseSendMessageApiResponse(data) {
        try {
            var status = data["query"]["results"]["json"]["status"];
            return status === "success";
        }
        catch (err) {
            return null;
        }
    }
    api.parseSendMessageApiResponse = parseSendMessageApiResponse;
})(api || (api = {}));
//# sourceMappingURL=api.js.map