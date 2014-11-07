/// <reference path="_references.ts" />
var settings;
(function (settings) {
    /**
     * The local storage key for the API username setting.
     * @type {string}
     */
    var API_USERNAME_KEY = "username";
    /**
     * The local storage key for the API password setting.
     * @type {string}
     */
    var API_PASSWORD_KEY = "password";
    /**
     * The local storage key to use for the local phone number setting.
     * @type {string}
     */
    var LOCAL_PHONE_NUMBER_KEY = "localPhoneNumber";
    /**
     * The local storage key for the days of messages history setting.
     * @type {string}
     */
    var DAYS_OF_MESSAGES_HISTORY_KEY = "messagesHistory";
    /**
     * The local storage key for the poll rate setting.
     * @type {string}
     */
    var POLL_RATE_KEY = "pollRate";
    /**
     * Gets the API username.
     * @returns {string} The API username.
     */
    function getUsername() {
        var username = window.localStorage.getItem(API_USERNAME_KEY);
        return username === null ? "" : username;
    }
    settings.getUsername = getUsername;
    /**
     * Sets the API username.
     * @param username The API username.
     */
    function setUsername(username) {
        window.localStorage.setItem(API_USERNAME_KEY, username);
    }
    settings.setUsername = setUsername;
    /**
     * Gets the API password.
     * @returns {string} The API password.
     */
    function getPassword() {
        var password = window.localStorage.getItem(API_PASSWORD_KEY);
        return password === null ? "" : password;
    }
    settings.getPassword = getPassword;
    /**
     * Sets the API password.
     * @param password The API password.
     */
    function setPassword(password) {
        window.localStorage.setItem(API_PASSWORD_KEY, password);
    }
    settings.setPassword = setPassword;
    /**
     * Gets the local phone number for use in the application.
     * @returns {string} The local phone number.
     */
    function getLocalPhoneNumber() {
        var phoneNumber = window.localStorage.getItem(LOCAL_PHONE_NUMBER_KEY);
        return phoneNumber === null ? "" : phoneNumber;
    }
    settings.getLocalPhoneNumber = getLocalPhoneNumber;
    /**
     * Sets the local phone number for use in the application.
     * @param phoneNumber The local phone number.
     */
    function setLocalPhoneNumber(phoneNumber) {
        window.localStorage.setItem(LOCAL_PHONE_NUMBER_KEY, phoneNumber);
    }
    settings.setLocalPhoneNumber = setLocalPhoneNumber;
    /**
     * Gets the days of messages history to retrieve. The default is 90.
     * @returns {number} The days of messages history to retrieve.
     */
    function getMessagesHistory() {
        var history = parseInt(window.localStorage.getItem(DAYS_OF_MESSAGES_HISTORY_KEY));
        return isNaN(history) ? 90 : history;
    }
    settings.getMessagesHistory = getMessagesHistory;
    /**
     * Sets the days of messages history to retrieve.
     * @param history The days of messages history to retrieve.
     */
    function setMessagesHistory(history) {
        window.localStorage.setItem(DAYS_OF_MESSAGES_HISTORY_KEY, String(history));
    }
    settings.setMessagesHistory = setMessagesHistory;
    /**
     * Gets the poll rate (in minutes). The default is 5.
     * @returns {string} The poll rate (in minutes).
     */
    function getPollRate() {
        var pollRate = parseInt(window.localStorage.getItem(POLL_RATE_KEY));
        return isNaN(pollRate) ? 5 : pollRate;
    }
    settings.getPollRate = getPollRate;
    /**
     * Sets the poll rate (in minutes).
     * @param pollRate The poll rate (in minutes).
     */
    function setPollRate(pollRate) {
        window.localStorage.setItem(POLL_RATE_KEY, String(pollRate));
        // Refresh monitor after monitor setting change
        polling.monitor();
    }
    settings.setPollRate = setPollRate;
})(settings || (settings = {}));
//# sourceMappingURL=settings.js.map