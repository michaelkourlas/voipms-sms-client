/// <reference path="../_references.ts" />

class MainInterface {
    private static LOADING_KEY: string = "loading";

    public static interval: number;

    static initialize(): void {
        var doc = $(document);
        var body = $("body");
        var menu = $("#menu");

        doc.on("menubutton", function() {
            if (!MainInterface.isLoading()) {
                if (body.pagecontainer("getActivePage").attr("id") === "page-conversations") {
                    menu.panel("toggle");
                }
            }
        });

        doc.on("backbutton", function() {
            if (!MainInterface.isLoading()) {
                history.back();
            }
        });

        $(".button-back").on("click", function() {
            history.back();
        });

        $(".button-menu").on("click", function() {
            menu.panel("toggle");
        });

        $("#button-menu-refresh").on("click", function() {
            ConversationsInterface.displayConversations();
        });

        $(".external-link").on("click", function() {
            window.open($(this).attr("data-link"), "_system");
        });

        Phone.refreshContacts(function() {
            NewConversationInterface.initialize();
            SettingsInterface.initialize();
            ConversationInterface.initialize();
            ConversationsInterface.initialize();

            MainInterface.monitor();
        });

    }

    static monitor() {
        window.clearInterval(MainInterface.interval);
        MainInterface.interval = window.setInterval(function() {
            Conversations.refreshConversations(function() {
                MainInterface.monitor();
            });
        }, Settings.getPollRate() * 60 * 1000);
    }

    static isLoading(): boolean {
        return window.localStorage.getItem(MainInterface.LOADING_KEY) === "true";
    }

    static showLoadingWidget(): void {
        $("body").pagecontainer("getActivePage").addClass('ui-disabled');
        $.mobile.loading("show", {
            textVisible: true
        });
        window.localStorage.setItem(MainInterface.LOADING_KEY, String(true));
    }

    static hideLoadingWidget(): void {
        $("body").pagecontainer("getActivePage").removeClass('ui-disabled');
        $.mobile.loading("hide");
        window.localStorage.setItem(MainInterface.LOADING_KEY, String(false));
    }

    static showToastNotification(text: string, type: string = "error", layout: string = "bottom"): void {
        var notification = noty({
            text: text,
            type: type,
            layout: layout
        });
        notification.show();
    }

    static showStatusBarNotification(id: number, title: String, message: String, count: number, json: String): void {
        window.plugin.notification.local.add({
            id: String(id),
            title: title,
            message: message,
            badge: count,
            json: json,
            autoCancel: true
        });
    }

    static hideStatusBarNotification(id: number) {
        window.plugin.notification.local.cancel(id);
    }
}