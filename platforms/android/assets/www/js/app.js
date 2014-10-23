/// <reference path="../third-party/async/async.d.ts" />
/// <reference path="../third-party/cordova/cordova.d.ts" />
/// <reference path="../third-party/jquery/jquery.d.ts" />
/// <reference path="../third-party/jquerymobile/jquerymobile.d.ts" />
/// <reference path="../third-party/jquery.noty/jquery.noty.d.ts" />
/// <reference path="../third-party/moment/moment.d.ts" />
/// <reference path="interface.ts" />
/// <reference path="settings.ts" />
$(document).ready(function () {
    $(document).on("deviceready", function () {
        $("body").on("pagecontainerchange", function (event, ui) {
            if (ui.toPage.attr("id") === "page-conversation") {
                $.mobile.silentScroll(ui.toPage.height());
            }
            else if (ui.toPage.attr("id") === "page-settings") {
                $("#textbox-settings-username").val(Settings.getUsername());
                $("#textbox-settings-password").val(Settings.getPassword());
            }
        });
        $(".button-back").on("click", function () {
            history.back();
        });
        $(".button-menu").on("click", function () {
            //noinspection TaskProblemsInspection
            $("#menu").panel("close");
        });
        $("#button-menu-refresh").on("click", function () {
            Interface.displayConversations();
        });
        $("#button-settings-save").on("click", function () {
            Settings.setUsername($("#textbox-settings-username").val());
            Settings.setPassword($("#textbox-settings-password").val());
        });
        $(".external-link").on("click", function () {
            window.open($(this).attr("data-link"), "_system");
        });
        Interface.displayConversations(true);
    });
});
//# sourceMappingURL=app.js.map