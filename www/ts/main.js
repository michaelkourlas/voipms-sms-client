/// <reference path="_references.ts" />
$(document).on("ready", function () {
    $(document).on("deviceready", function () {
        ui.initialize();
        contacts.refreshContacts(function () {
            ui.conversationspage.display();
            polling.initialize();
        });
        $(".iscroll-wrapper").bind({
            iscroll_onpulldown: ui.onPullDown,
            iscroll_onpullup: ui.onPullUp
        });
    });
});
//# sourceMappingURL=main.js.map