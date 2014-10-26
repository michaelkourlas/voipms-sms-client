/// <reference path="_references.ts" />

/**
 * Represents the settings of the application.
 */
class Settings {
    /**
     * The local storage key for the API username setting.
     */
    private static API_USERNAME_KEY: string = "username";
    /**
     * The local storage key for the API password setting.
     */
    private static API_PASSWORD_KEY: string = "password";
    /**
     * The local storage key to use for the local phone number setting.
     */
    private static LOCAL_PHONE_NUMBER_KEY: string = "localPhoneNumber";
    /**
     * The local storage key for the days of messages history setting.
     */
    private static DAYS_OF_MESSAGES_HISTORY_KEY: string = "messagesHistory";
    /**
     * The local storage key for the poll rate setting.
     */
    private static POLL_RATE_KEY: string = "pollRate";

    /**
     * Gets the API username.
     * @returns {string} The API username.
     */
    public static getUsername(): string {
        var username = window.localStorage.getItem(Settings.API_USERNAME_KEY);
        return username === null ? "" : username;
    }

    /**
     * Sets the API username.
     * @param username The API username.
     */
    public static setUsername(username: string) {
        window.localStorage.setItem(Settings.API_USERNAME_KEY, username);
    }

    /**
     * Gets the API password.
     * @returns {string} The API password.
     */
    public static getPassword(): string {
        var password = window.localStorage.getItem(Settings.API_PASSWORD_KEY);
        return password === null ? "" : password;
    }

    /**
     * Sets the API password.
     * @param password The API password.
     */
    public static setPassword(password: string) {
        window.localStorage.setItem(Settings.API_PASSWORD_KEY, password);
    }

    /**
     * Gets the local phone number for use in the application.
     * @returns {string} The local phone number.
     */
    static getLocalPhoneNumber(): string {
        var phoneNumber = window.localStorage.getItem(Settings.LOCAL_PHONE_NUMBER_KEY);
        return phoneNumber === null ? "" : phoneNumber;
    }

    /**
     * Sets the local phone number for use in the application.
     * @param phoneNumber The local phone number.
     */
    static setLocalPhoneNumber(phoneNumber: string): void {
        window.localStorage.setItem(Settings.LOCAL_PHONE_NUMBER_KEY, phoneNumber);
    }

    /**
     * Gets the days of messages history to retrieve. The default is 90.
     * @returns {number} The days of messages history to retrieve.
     */
    static getMessagesHistory(): number {
        var history: number = parseInt(window.localStorage.getItem(Settings.DAYS_OF_MESSAGES_HISTORY_KEY));
        return isNaN(history) ? 90 : history;
    }

    /**
     * Sets the days of messages history to retrieve.
     * @param history The days of messages history to retrieve.
     */
    static setMessagesHistory(history: number) {
        window.localStorage.setItem(Settings.DAYS_OF_MESSAGES_HISTORY_KEY, String(history));
    }

    /**
     * Gets the poll rate (in minutes). The default is 5.
     * @returns {string} The poll rate (in minutes).
     */
    static getPollRate(): number {
        var pollRate: number = parseInt(window.localStorage.getItem(Settings.POLL_RATE_KEY));
        return isNaN(pollRate) ? 5 : pollRate;
    }

    /**
     * Sets the poll rate (in minutes).
     * @param pollRate The poll rate (in minutes).
     */
    static setPollRate(pollRate: number) {
        window.localStorage.setItem(Settings.POLL_RATE_KEY, String(pollRate));
        MainInterface.monitor();
    }
}