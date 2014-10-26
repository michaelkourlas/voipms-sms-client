/// <reference path="_references.ts" />
/**
 * Represents the settings of the application.
 */
var Settings = (function () {
    function Settings() {
    }
    /**
     * Gets the API username.
     * @returns {string} The API username.
     */
    Settings.getUsername = function () {
        var username = window.localStorage.getItem(Settings.API_USERNAME_KEY);
        return username === null ? "" : username;
    };
    /**
     * Sets the API username.
     * @param username The API username.
     */
    Settings.setUsername = function (username) {
        window.localStorage.setItem(Settings.API_USERNAME_KEY, username);
    };
    /**
     * Gets the API password.
     * @returns {string} The API password.
     */
    Settings.getPassword = function () {
        var password = window.localStorage.getItem(Settings.API_PASSWORD_KEY);
        return password === null ? "" : password;
    };
    /**
     * Sets the API password.
     * @param password The API password.
     */
    Settings.setPassword = function (password) {
        window.localStorage.setItem(Settings.API_PASSWORD_KEY, password);
    };
    /**
     * Gets the local phone number for use in the application.
     * @returns {string} The local phone number.
     */
    Settings.getLocalPhoneNumber = function () {
        var phoneNumber = window.localStorage.getItem(Settings.LOCAL_PHONE_NUMBER_KEY);
        return phoneNumber === null ? "" : phoneNumber;
    };
    /**
     * Sets the local phone number for use in the application.
     * @param phoneNumber The local phone number.
     */
    Settings.setLocalPhoneNumber = function (phoneNumber) {
        window.localStorage.setItem(Settings.LOCAL_PHONE_NUMBER_KEY, phoneNumber);
    };
    /**
     * Gets the days of messages history to retrieve. The default is 90.
     * @returns {number} The days of messages history to retrieve.
     */
    Settings.getMessagesHistory = function () {
        var history = parseInt(window.localStorage.getItem(Settings.DAYS_OF_MESSAGES_HISTORY_KEY));
        return isNaN(history) ? 90 : history;
    };
    /**
     * Sets the days of messages history to retrieve.
     * @param history The days of messages history to retrieve.
     */
    Settings.setMessagesHistory = function (history) {
        window.localStorage.setItem(Settings.DAYS_OF_MESSAGES_HISTORY_KEY, String(history));
    };
    /**
     * Gets the poll rate (in minutes). The default is 5.
     * @returns {string} The poll rate (in minutes).
     */
    Settings.getPollRate = function () {
        var pollRate = parseInt(window.localStorage.getItem(Settings.POLL_RATE_KEY));
        return isNaN(pollRate) ? 5 : pollRate;
    };
    /**
     * Sets the poll rate (in minutes).
     * @param pollRate The poll rate (in minutes).
     */
    Settings.setPollRate = function (pollRate) {
        window.localStorage.setItem(Settings.POLL_RATE_KEY, String(pollRate));
        MainInterface.monitor();
    };
    /**
     * The local storage key for the API username setting.
     */
    Settings.API_USERNAME_KEY = "username";
    /**
     * The local storage key for the API password setting.
     */
    Settings.API_PASSWORD_KEY = "password";
    /**
     * The local storage key to use for the local phone number setting.
     */
    Settings.LOCAL_PHONE_NUMBER_KEY = "localPhoneNumber";
    /**
     * The local storage key for the days of messages history setting.
     */
    Settings.DAYS_OF_MESSAGES_HISTORY_KEY = "messagesHistory";
    /**
     * The local storage key for the poll rate setting.
     */
    Settings.POLL_RATE_KEY = "pollRate";
    return Settings;
})();
//# sourceMappingURL=Settings.js.map