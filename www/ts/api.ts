/// <reference path="_references.ts" />

/**
 * Interface to access the VoIP.ms API.
 */
module api {
    /**
     * Default SMS retrieval limit.
     * @type {number}
     */
    var DEFAULT_LIMIT: number = 10000;
    /**
     * Increment for SMS retrieval limit increases.
     * @type {number}
     */
    var LIMIT_INCREMENT: number = 1000;

    /**
     * Gets a list of conversations from the VoIP.ms API.
     * @param username The API username.
     * @param password The API password.
     * @param history The number of days of history to retrieve.
     * @param localPhoneNumber The local phone number.
     * @param callback A callback function returning the conversations and an error string. The conversations variable
     * will be null if there is an error, while the error variable will be null if there is no error.
     */
    export function getConversations(username: string, password: string, history: number, localPhoneNumber: string,
                                     callback: (conversations: Conversation[], err: string) => void): void {
        // Continue making API requests until limit exceeds number of messages returned
        var limitArr: number[] = [DEFAULT_LIMIT];
        async.eachSeries(limitArr, function(limit, limitCallback) {
            var url: string = api.createGetConversationsApiUrl(username, password, limit, history, localPhoneNumber);
            var request = $.getJSON(url);
            request.done(function(data: Object) {
                var conversations: Conversation[] = api.parseGetConversationsApiResponse(data);
                if (conversations != null) {
                    var messageCount: number = 0;
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
                    callback(null, "Error decoding VoIP.ms API response. Are your username, password, and selected " +
                    "phone number correct?");
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
     * Creates an API URL for the getSMS method.
     * @param username The API username.
     * @param password The API password.
     * @param limit The total number of messages to retrieve.
     * @param history The number of days of history to retrieve.
     * @param localPhoneNumber The local phone number.
     * @returns {string} The URL for the getSMS method.
     */
    export function createGetConversationsApiUrl(username: string, password: string, limit: number, history: number,
                                                 localPhoneNumber: string): string {
        var startDate: Moment = moment().utc().subtract(history, "day");
        var endDate: Moment = moment().utc();

        return "https://www.voip.ms/api/v1/rest.php?" + "&" +
            "api_username=" + encodeURIComponent(username) + "&" +
            "api_password=" + encodeURIComponent(password) + "&" +
            "method=getSMS" + "&" +
            "did=" + encodeURIComponent(localPhoneNumber) + "&" +
            "limit=" + encodeURIComponent(String(limit)) + "&" +
            "from=" + encodeURIComponent(startDate.toISOString().substr(0, 10)) + "&" +
            "to=" + encodeURIComponent(endDate.toISOString().substr(0, 10))
    }

    /**
     * Parses the getSMS API response.
     * @param data The API response.
     * @returns {*} A list of conversations.
     */
    export function parseGetConversationsApiResponse(data: any): Conversation[] {
        try {
            var conversations: Conversation[] = [];
            var rawMessages = data["sms"];

            if (!(rawMessages instanceof Array)) {
                rawMessages = [rawMessages];
            }

            for (var i = rawMessages.length - 1; i >= 0; i--) {
                var conversation: Conversation = null;
                for (var j = 0; j < conversations.length; j++) {
                    if (conversations[j].getRemotePhoneNumber() === rawMessages[i]["contact"]) {
                        conversation = conversations[j];
                    }
                }
                if (conversation === null) {
                    conversation = new Conversation();
                    conversations.push(conversation);
                }

                var message: Message = new Message(parseInt(rawMessages[i]["id"]), rawMessages[i]["message"],
                    moment(rawMessages[i]["date"]), parseInt(rawMessages[i]["type"]) === 0 ? MessageType.Outgoing :
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

    /**
     * Gets a list of local phone numbers attached to a VoIP.ms account.
     * @param username The API username.
     * @param password The API password.
     * @param callback A callback function returning the phone numbers and an error string. The conversations variable
     * will be null if there is an error, while the error variable will be null if there is no error.
     */
    export function getLocalPhoneNumbers(username: string, password: string,
                                         callback: (phoneNumbers: string[], err: string) => void) {
        var url: string = api.createGetLocalPhoneNumbersApiUrl(username, password);
        var request = $.getJSON(url);
        request.done(function(data: Object) {
            var phoneNumbers: string[] = api.parseGetLocalPhoneNumbersApiResponse(data);
            if (phoneNumbers != null) {
                callback(phoneNumbers, null);
            }
            else {
                callback(null, "Error decoding VoIP.ms API response. Are your username, password, and selected phone " +
                "number correct?");
            }
        });
        request.fail(function() {
            callback(null, "Error accessing VoIP.ms API. Are you connected to the Internet?");
        });
    }

    /**
     * Creates an API URL for the getDIDsInfo method.
     * @param username The API username.
     * @param password The API password.
     * @returns {string} The URL for the getDIDsInfo method.
     */
    export function createGetLocalPhoneNumbersApiUrl(username: string, password: string) {
        return "https://www.voip.ms/api/v1/rest.php?" + "&" +
            "api_username=" + encodeURIComponent(username) + "&" +
            "api_password=" + encodeURIComponent(password) + "&" +
            "method=getDIDsInfo";
    }

    /**
     * Parses the getDIDsInfo API response.
     * @param data The API response.
     * @returns {*} The phone numbers.
     */
    export function parseGetLocalPhoneNumbersApiResponse(data: any): string[] {
        try {
            var phoneNumbers: string[] = [];
            var rawPhoneNumbers = data["dids"];

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
    export function sendSms(username: string, password: string, localPhoneNumber: string, remotePhoneNumber: string,
                            messageText: string, callback: (successful: boolean, err: string) => void) {
        var url: string = api.createSendMessageApiUrl(username, password, localPhoneNumber, remotePhoneNumber,
            messageText);
        var request = $.getJSON(url);
        request.done(function(data: Object) {
            var success: boolean = api.parseSendMessageApiResponse(data);
            if (success != null) {
                callback(success, null);
            }
            else {
                callback(null, "Error decoding VoIP.ms API response. Are your username, password, and selected phone " +
                "number correct?");
            }
        });
        request.fail(function() {
            callback(null, "Error accessing VoIP.ms API. Are you connected to the Internet?");
        });
    }

    /**
     * Creates an API URL for the sendSMS method.
     * @param username The API username.
     * @param password The API password.
     * @param localPhoneNumber The local phone number.
     * @param remotePhoneNumber The remote phone number.
     * @param messageText The text of the message.
     * @returns {string} The URL for the sendSMS method.
     */
    export function createSendMessageApiUrl(username: string, password: string, localPhoneNumber: string,
                                            remotePhoneNumber: string, messageText: string) {
        return "https://www.voip.ms/api/v1/rest.php?" + "&" +
            "api_username=" + encodeURIComponent(username) + "&" +
            "api_password=" + encodeURIComponent(password) + "&" +
            "method=sendSMS" + "&" +
            "did=" + encodeURIComponent(localPhoneNumber) + "&" +
            "dst=" + encodeURIComponent(remotePhoneNumber) + "&" +
            "message=" + encodeURIComponent(messageText);
    }

    /**
     * Parses the sendSMS API response.
     * @param data The API response.
     * @returns {*} Whether or not the SMS was successfully sent.
     */
    export function parseSendMessageApiResponse(data: any): boolean {
        try {
            var status = data["status"];
            return status === "success";
        }
        catch (err) {
            return null;
        }
    }
}
