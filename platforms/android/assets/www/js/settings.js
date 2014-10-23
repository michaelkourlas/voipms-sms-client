/**
 * Provides access to the application's settings.
 */
var Settings = (function () {
    function Settings() {
    }
    /**
     * Gets the API username.
     * @returns {string} The API username.
     */
    Settings.getUsername = function () {
        var username = window.localStorage.getItem(Settings.USERNAME_KEY);
        return username === null ? "" : username;
    };
    /**
     * Sets the API username.
     * @param username The API username.
     */
    Settings.setUsername = function (username) {
        window.localStorage.setItem(Settings.USERNAME_KEY, username);
    };
    /**
     * Gets the API password.
     * @returns {string} The API password.
     */
    Settings.getPassword = function () {
        var password = window.localStorage.getItem(Settings.PASSWORD_KEY);
        return password === null ? "" : password;
    };
    /**
     * Sets the API password.
     * @param password The API password.
     */
    Settings.setPassword = function (password) {
        window.localStorage.setItem(Settings.PASSWORD_KEY, password);
    };
    /**
     * Gets the SMS poll rate (in minutes). The default is 5.
     * @returns {string} The SMS poll rate (in minutes).
     */
    Settings.getPollRate = function () {
        var pollRate = parseInt(window.localStorage.getItem(Settings.POLL_RATE_KEY));
        return isNaN(pollRate) ? 5 : pollRate;
    };
    /**
     * Sets the SMS poll rate (in minutes).
     * @returns {string} The SMS poll rate (in minutes).
     */
    Settings.setPollRate = function (pollRate) {
        window.localStorage.setItem(Settings.POLL_RATE_KEY, String(pollRate));
    };
    /**
     * Gets the number of days to get SMS history (in minutes). The default is 90.
     * @returns {string} The SMS poll rate (in minutes).
     */
    Settings.getHistory = function () {
        var history = parseInt(window.localStorage.getItem(Settings.HISTORY_KEY));
        return isNaN(history) ? 90 : history;
    };
    /**
     * Sets the SMS poll rate (in minutes).
     * @returns {string} The SMS poll rate (in minutes).
     */
    Settings.setHistory = function (history) {
        window.localStorage.setItem(Settings.HISTORY_KEY, String(history));
    };
    /**
     * The local storage key for the username setting.
     */
    Settings.USERNAME_KEY = "username";
    /**
     * The local storage key for the password setting.
     */
    Settings.PASSWORD_KEY = "password";
    /**
     * The local storage key for the poll rate setting.
     */
    Settings.POLL_RATE_KEY = "pollRate";
    /**
     * The local storage key for the history setting.
     */
    Settings.HISTORY_KEY = "history";
    return Settings;
})();
//# sourceMappingURL=settings.js.map