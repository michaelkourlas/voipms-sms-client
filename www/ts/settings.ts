/// <reference path="_references.ts" />

module settings {
    /**
     * The local storage key for the API username setting.
     * @type {string}
     */
    var API_USERNAME_KEY: string = "username";

    /**
     * The local storage key for the API password setting.
     * @type {string}
     */
    var API_PASSWORD_KEY: string = "password";

    /**
     * The local storage key to use for the local phone number setting.
     * @type {string}
     */
    var LOCAL_PHONE_NUMBER_KEY: string = "localPhoneNumber";

    /**
     * The local storage key for the days of messages history setting.
     * @type {string}
     */
    var DAYS_OF_MESSAGES_HISTORY_KEY: string = "messagesHistory";
    /**
     * The local storage key for the poll rate setting.
     * @type {string}
     */
    var POLL_RATE_KEY: string = "pollRate";

    /**
     * Gets the API username.
     * @returns {string} The API username.
     */
    export function getUsername(): string {
        var username = window.localStorage.getItem(API_USERNAME_KEY);
        return username === null ? "" : username;
    }

    /**
     * Sets the API username.
     * @param username The API username.
     */
    export function setUsername(username: string) {
        window.localStorage.setItem(API_USERNAME_KEY, username);
    }

    /**
     * Gets the API password.
     * @returns {string} The API password.
     */
    export function getPassword(): string {
        var password = window.localStorage.getItem(API_PASSWORD_KEY);
        return password === null ? "" : password;
    }

    /**
     * Sets the API password.
     * @param password The API password.
     */
    export function setPassword(password: string) {
        window.localStorage.setItem(API_PASSWORD_KEY, password);
    }

    /**
     * Gets the local phone number for use in the application.
     * @returns {string} The local phone number.
     */
    export function getLocalPhoneNumber(): string {
        var phoneNumber = window.localStorage.getItem(LOCAL_PHONE_NUMBER_KEY);
        return phoneNumber === null ? "" : phoneNumber;
    }

    /**
     * Sets the local phone number for use in the application.
     * @param phoneNumber The local phone number.
     */
    export function setLocalPhoneNumber(phoneNumber: string): void {
        window.localStorage.setItem(LOCAL_PHONE_NUMBER_KEY, phoneNumber);
    }

    /**
     * Gets the days of messages history to retrieve. The default is 90.
     * @returns {number} The days of messages history to retrieve.
     */
    export function getMessagesHistory(): number {
        var history: number = parseInt(window.localStorage.getItem(DAYS_OF_MESSAGES_HISTORY_KEY));
        return isNaN(history) ? 90 : history;
    }

    /**
     * Sets the days of messages history to retrieve.
     * @param history The days of messages history to retrieve.
     */
    export function setMessagesHistory(history: number) {
        window.localStorage.setItem(DAYS_OF_MESSAGES_HISTORY_KEY, String(history));
    }

    /**
     * Gets the poll rate (in minutes). The default is 5.
     * @returns {string} The poll rate (in minutes).
     */
    export function getPollRate(): number {
        var pollRate: number = parseInt(window.localStorage.getItem(POLL_RATE_KEY));
        return isNaN(pollRate) ? 5 : pollRate;
    }

    /**
     * Sets the poll rate (in minutes).
     * @param pollRate The poll rate (in minutes).
     */
    export function setPollRate(pollRate: number) {
        window.localStorage.setItem(POLL_RATE_KEY, String(pollRate));

        // Refresh monitor after monitor setting change
        polling.monitor();
    }
}