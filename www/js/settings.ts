/**
 * Provides access to the application's settings.
 */
class Settings {
    /**
     * The local storage key for the username setting.
     */
    private static USERNAME_KEY: string = "username";
    /**
     * The local storage key for the password setting.
     */
    private static PASSWORD_KEY: string = "password";
    /**
     * The local storage key for the poll rate setting.
     */
    private static POLL_RATE_KEY: string = "pollRate";
    /**
     * The local storage key for the history setting.
     */
    private static HISTORY_KEY: string = "history";

    /**
     * Gets the API username.
     * @returns {string} The API username.
     */
    static getUsername(): string {
        var username = window.localStorage.getItem(Settings.USERNAME_KEY);
        return username === null ? "" : username;
    }

    /**
     * Sets the API username.
     * @param username The API username.
     */
    static setUsername(username: string) {
        window.localStorage.setItem(Settings.USERNAME_KEY, username);
    }

    /**
     * Gets the API password.
     * @returns {string} The API password.
     */
    static getPassword(): string {
        var password = window.localStorage.getItem(Settings.PASSWORD_KEY);
        return password === null ? "" : password;
    }

    /**
     * Sets the API password.
     * @param password The API password.
     */
    static setPassword(password: string) {
        window.localStorage.setItem(Settings.PASSWORD_KEY, password);
    }

    /**
     * Gets the SMS poll rate (in minutes). The default is 5.
     * @returns {string} The SMS poll rate (in minutes).
     */
    static getPollRate(): number {
        var pollRate: number = parseInt(window.localStorage.getItem(Settings.POLL_RATE_KEY));
        return isNaN(pollRate) ? 5 : pollRate;
    }

    /**
     * Sets the SMS poll rate (in minutes).
     * @returns {string} The SMS poll rate (in minutes).
     */
    static setPollRate(pollRate: number) {
        window.localStorage.setItem(Settings.POLL_RATE_KEY, String(pollRate));
    }

    /**
     * Gets the number of days to get SMS history (in minutes). The default is 90.
     * @returns {string} The SMS poll rate (in minutes).
     */
    static getHistory(): number {
        var history: number = parseInt(window.localStorage.getItem(Settings.HISTORY_KEY));
        return isNaN(history) ? 90 : history;
    }

    /**
     * Sets the SMS poll rate (in minutes).
     * @returns {string} The SMS poll rate (in minutes).
     */
    static setHistory(history: number) {
        window.localStorage.setItem(Settings.HISTORY_KEY, String(history));
    }
}