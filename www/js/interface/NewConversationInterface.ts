/// <reference path="../_references.ts" />

class NewConversationInterface {
    public static initialize() {
        $("body").on("pagecontainerchange", function(event, ui) {
            if (ui.toPage.attr("id") === "page-conversation") {
                $("#new-conversation-recipient-input").val("");
                $("#new-conversation-message-textarea").val("");
            }
        });

        $("#new-conversation-contacts-button").on("click", function() {
            window.plugins.ContactChooser.chooseContact(function (contactInfo) {
                if (contactInfo.phoneNumber === "") {
                    MainInterface.showToastNotification("Selected contact does not have a phone number.");
                }
                else {
                    var filteredPhoneNumber = contactInfo.phoneNumber.replace(/[^\d]/g, "").replace(
                        /^.*(\d{10})$/, "$1");
                    $("#new-conversation-recipient-input").val(filteredPhoneNumber);
                }
            });
        });

        $("#new-conversation-send-button").on("click", function() {
            var filteredPhoneNumber = $("#new-conversation-recipient-input").val().replace(/[^\d]/g, "").replace(
                /^.*(\d{10})$/, "$1");
            if (filteredPhoneNumber.length !== 10) {
                MainInterface.showToastNotification("Phone number is not valid.");
            }
            else if ($("#new-conversation-message-textarea").val().length > 160) {
                MainInterface.showToastNotification("Message exceeds 160 characters.");
            }
            else if ($("#new-conversation-message-textarea").val() === "") {
                MainInterface.showToastNotification("Message is empty.");
            }
            else {
                Api.sendSms(Settings.getUsername(), Settings.getPassword(), Settings.getLocalPhoneNumber(),
                    filteredPhoneNumber,
                    $("#new-conversation-message-textarea").val(), function(successful: boolean, err: string) {
                        if (successful) {
                            history.back();
                        } else {
                            MainInterface.showToastNotification(err);
                        }
                    }
                );
            }
        });
    }
}