/// <reference path="_references.ts" />
$(document).on("ready", function () {
    $(document).on("deviceready", function () {
        ui.initialize();
        contacts.refreshContacts(function () {
            ui.conversationspage.display();
            polling.initialize();
        });
    });
});
//# sourceMappingURL=main.js.map