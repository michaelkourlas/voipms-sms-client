/// <reference path="_references.ts" />

/**
 * Interface for periodically refreshing conversations.
 */
module polling {
    /**
     * The return value of setInterval when called for the purposes of message polling.
     */
    var pollingInterval: number;

    /**
     * Initializes the application's polling UI.
     */
    export function initialize() {
        monitor();
    }

    /**
     * Refreshes the application's conversations at periodic intervals.
     */
    export function monitor() {
        window.clearInterval(pollingInterval);
        pollingInterval = window.setInterval(function() {
            conversations.refresh(function() {
                monitor();
            });
        }, settings.getPollRate() * 60 * 1000);
    }
}