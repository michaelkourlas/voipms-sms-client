/// <reference path="../_references.ts" />

class SettingsInterface {
    public static initialize() {
        var body = $("body");

        var usernameInput = $("#settings-username-input");
        var passwordInput = $("#settings-password-input");
        var phoneNumberInput = $("#settings-phone-number-input");
        var phoneNumbersButton = $("#settings-phone-numbers-button");
        var historyInput = $("#settings-history-input");
        var pollRateInput = $("#settings-poll-rate-input");
        var saveButton = $("#settings-save-button");

        var phoneNumbersForm = $("#settings-phone-numbers-form");
        var phoneNumbersFieldset = $("#settings-phone-numbers-fieldset");
        var phoneNumbersSelectButton = $("#settings-phone-numbers-select-button");

        body.on("pagecontainerchange", function(event, ui) {
            if (ui.toPage.attr("id") === "page-settings") {
                usernameInput.val(Settings.getUsername());
                passwordInput.val(Settings.getPassword());
                phoneNumberInput.val(Settings.getLocalPhoneNumber());
                historyInput.val(String(Settings.getMessagesHistory()));
                pollRateInput.val(String(Settings.getPollRate()));
            }
        });

        phoneNumbersButton.on("click", function() {
            Api.getLocalPhoneNumbers(Settings.getUsername(), Settings.getPassword(), function(phoneNumbers, err) {
                if (err === null) {
                    if (phoneNumbers.length === 0) {
                        MainInterface.showToastNotification("No phone numbers available. Is SMS enabled on the phone" +
                        "number you wish to use?");
                    }
                    else {
                        phoneNumbersFieldset.empty();
                        for (var i = 0; i < phoneNumbers.length; i++) {
                            phoneNumbersFieldset.append($("<input id=\"settings-phone-numbers-radio-button-" + i +
                            "\" name=\"" + phoneNumbers[i] + "\" type=\"radio\">"));
                            phoneNumbersFieldset.append($("<label for=\"settings-phone-numbers-radio-button-" + i +
                            "\">" + phoneNumbers[i] + "</label>"));
                        }
                        phoneNumbersFieldset.children().first().prop("checked", true);
                        phoneNumbersForm.trigger("create");

                        body.pagecontainer("change", "#page-settings-phone-numbers");
                    }
                }
                else {
                    MainInterface.showToastNotification(err);
                }
            });
        });

        phoneNumbersSelectButton.on("click", function() {
            Settings.setLocalPhoneNumber($('input:checked', '#settings-phone-numbers-fieldset').attr("name"));
            history.back();
        });

        saveButton.on("click", function() {
            if (isNaN(historyInput.val()) || parseInt(historyInput.val()) <= 0 || historyInput.val() > 90) {
                MainInterface.showToastNotification("Number of days of SMS history to retrieve must be an integer greater" +
                "than 0 and less than or equal to 90.");
            }
            else if (isNaN(pollRateInput.val()) || parseInt(pollRateInput.val()) < 0) {
                MainInterface.showToastNotification("SMS poll rate must be an integer greater than or equal to 0.");
            }
            else {
                Settings.setUsername(usernameInput.val());
                Settings.setPassword(passwordInput.val());
                Settings.setMessagesHistory(historyInput.val());
                Settings.setPollRate(pollRateInput.val());
            }
        });
    }
}