/// <reference path="../_references.ts" />
var MainInterface = (function () {
    function MainInterface() {
    }
    MainInterface.initialize = function () {
        var doc = $(document);
        var body = $("body");
        var menu = $("#menu");
        doc.on("menubutton", function () {
            if (!MainInterface.isLoading()) {
                if (body.pagecontainer("getActivePage").attr("id") === "page-conversations") {
                    menu.panel("toggle");
                }
            }
        });
        doc.on("backbutton", function () {
            if (!MainInterface.isLoading()) {
                history.back();
            }
        });
        $(".button-back").on("click", function () {
            history.back();
        });
        $(".button-menu").on("click", function () {
            menu.panel("toggle");
        });
        $("#button-menu-refresh").on("click", function () {
            ConversationsInterface.displayConversations();
        });
        $(".external-link").on("click", function () {
            window.open($(this).attr("data-link"), "_system");
        });
        Phone.refreshContacts(function () {
            NewConversationInterface.initialize();
            SettingsInterface.initialize();
            ConversationInterface.initialize();
            ConversationsInterface.initialize();
            MainInterface.monitor();
        });
    };
    MainInterface.monitor = function () {
        window.clearInterval(MainInterface.interval);
        MainInterface.interval = window.setInterval(function () {
            Conversations.refreshConversations(function () {
                MainInterface.monitor();
            });
        }, Settings.getPollRate() * 60 * 1000);
    };
    MainInterface.isLoading = function () {
        return window.localStorage.getItem(MainInterface.LOADING_KEY) === "true";
    };
    MainInterface.showLoadingWidget = function () {
        $("body").pagecontainer("getActivePage").addClass('ui-disabled');
        $.mobile.loading("show", {
            textVisible: true
        });
        window.localStorage.setItem(MainInterface.LOADING_KEY, String(true));
    };
    MainInterface.hideLoadingWidget = function () {
        $("body").pagecontainer("getActivePage").removeClass('ui-disabled');
        $.mobile.loading("hide");
        window.localStorage.setItem(MainInterface.LOADING_KEY, String(false));
    };
    MainInterface.showToastNotification = function (text, type, layout) {
        if (type === void 0) { type = "error"; }
        if (layout === void 0) { layout = "bottom"; }
        var notification = noty({
            text: text,
            type: type,
            layout: layout
        });
        notification.show();
    };
    MainInterface.showStatusBarNotification = function (id, title, message, count, json) {
        window.plugin.notification.local.add({
            id: String(id),
            title: title,
            message: message,
            badge: count,
            json: json,
            autoCancel: true
        });
    };
    MainInterface.hideStatusBarNotification = function (id) {
        window.plugin.notification.local.cancel(id);
    };
    MainInterface.LOADING_KEY = "loading";
    return MainInterface;
})();
//# sourceMappingURL=MainInterface.js.map