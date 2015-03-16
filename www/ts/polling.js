/// <reference path="_references.ts" />
/**
 * Interface for periodically refreshing conversations.
 */
var polling;
(function (polling) {
    /**
     * The return value of setInterval when called for the purposes of message polling.
     */
    var pollingInterval;
    /**
     * Initializes the application's polling UI.
     */
    function initialize() {
        monitor();
    }
    polling.initialize = initialize;
    /**
     * Refreshes the application's conversations at periodic intervals.
     */
    function monitor() {
        window.clearInterval(pollingInterval);
        if (settings.getPollRate() > 0) {
            pollingInterval = window.setInterval(function () {
                conversations.refresh(function () {
                    monitor();
                });
            }, settings.getPollRate() * 60 * 1000);
        }
    }
    polling.monitor = monitor;
})(polling || (polling = {}));
//# sourceMappingURL=polling.js.map