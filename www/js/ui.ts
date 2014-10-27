/// <reference path="_references.ts" />

module ui {
    export function initialize() {
        menu.initialize();
        newconversationpage.initialize();
        notifications.initialize();
        settingspage.initialize();
        conversationpage.initialize();
        conversationspage.initialize();
        main.initialize();
    }

    export module conversationpage {
        /**
         * Initializes the application's conversation page.
         */
        export function initialize() {
            $("body").on("pagecontainerchange", function(event, ui) {
                if (ui.toPage.attr("id") === "page-conversation") {
                    $.mobile.silentScroll(ui.toPage.height());
                    $("#conversation-message-input").val("");
                }
            });

            $("#conversation-send-message-button").on("click", function() {
                var messageInput = $("#conversation-message-input");

                if (messageInput.val().length > 160) {
                    ui.notifications.showToastNotification("Message exceeds 160 characters.");
                }
                else if (messageInput.val() === "") {
                    ui.notifications.showToastNotification("Message is empty.");
                }
                else {
                    ui.loading.startLoading();
                    Api.sendSms(settings.getUsername(), settings.getPassword(), settings.getLocalPhoneNumber(),
                        conversations.getActiveConversationPhoneNumber(), messageInput.val(),
                        function(successful: boolean, err: string) {
                            if (successful) {
                                conversations.refresh(function() {
                                    var conversationsData = conversations.get();
                                    for (var i = 0; i < conversationsData.length; i++) {
                                        if (conversationsData[i].getRemotePhoneNumber() ===
                                            conversations.getActiveConversationPhoneNumber()) {
                                            display(conversationsData[i], function() {
                                                ui.loading.stopLoading();
                                            });
                                            break;
                                        }
                                    }

                                });
                            } else {
                                ui.notifications.showToastNotification(err);
                                ui.loading.stopLoading();
                            }
                        }
                    );
                }
            });
        }

        export function display(conversation: Conversation, callback: () => void = null) {
            ui.loading.startLoading();

            var header = $("#page-conversation-header");
            var listview = $("#conversation-listview");

            header.empty();
            listview.empty();

            contacts.getContact(null, conversation.messages[0].getRemotePhoneNumber(), function(contact) {
                if (contact !== null && contact.displayName !== null) {
                    header.append(contact.displayName);
                }
                else {
                    header.append(conversation.messages[0].getRemotePhoneNumber());
                }

                for (var i = 0; i < conversation.messages.length; i++) {
                    var listviewItem = $("<li>");
                    if (conversation.messages[i].type === MessageType.Outgoing) {
                        listviewItem.append($("<span>You</span>"));
                    }
                    else if (conversation.messages[i].type === MessageType.Incoming) {
                        if (contact !== null && contact.displayName !== null) {
                            listviewItem.append($("<span>" + contact.displayName + "</span>"));
                        }
                        else {
                            listviewItem.append($("<span>" + conversation.messages[i].getRemotePhoneNumber() +
                            "</span>"));
                        }
                    }

                    var date;
                    if (moment(conversation.messages[i].date).isSame(moment(), "day")) {
                        date = moment(conversation.messages[i].date).format("h:mm A");
                    }
                    else if (moment(conversation.messages[i].date).isSame(moment(), "year")) {
                        date = moment(conversation.messages[i].date).format("MMM D");
                    }
                    else {
                        date = moment(conversation.messages[i].date).format("YY/MM/DD");
                    }
                    listviewItem.append($("<p class=\"ui-li-aside date-time-container\">" +
                    "<span class=\"date-time\">" + date + "</span></p>"));
                    listviewItem.append($("<br>"));
                    listviewItem.append($("<p class=\"message\">" + conversation.messages[i].text + "</p>"));

                    listview.append(listviewItem);
                }

                listview.listview("refresh");
                ui.loading.stopLoading();

                $.mobile.silentScroll($("#page-conversation").height());
                $("#conversation-message-input").val("");

                if (callback !== null) {
                    callback();
                }
            });
        }
    }

    export module conversationspage {
        export function initialize() {
            $("body").on("pagecontainerchange", function(event, ui) {
                if (ui.toPage.attr("id") === "conversations-page") {
                    display();
                    $.mobile.silentScroll(0);
                }
            });
        }

        export function showConversation(phoneNumber: string) {
            var conversationsData = conversations.get();
            var selectedConversation: Conversation = null;
            var selectedConversationIndex: number = null;
            for (var i = 0; i < conversationsData.length; i++) {
                if (conversationsData[i].getRemotePhoneNumber() === phoneNumber) {
                    selectedConversation = conversationsData[i];
                    selectedConversationIndex = i;
                    break;
                }
            }

            if (selectedConversation !== null) {
                conversations.setActiveConversationPhoneNumber(selectedConversation.getRemotePhoneNumber());

                selectedConversation.markAllMessagesAsRead();
                conversationsData[selectedConversationIndex] = selectedConversation;
                conversations.set(conversationsData);

                var conversationPage = $("#page-conversation");
                $("body").pagecontainer("change", conversationPage);
                ui.conversationpage.display(selectedConversation);
            }
        }

        export function display(callback: () => void = null) {
            ui.loading.startLoading();
            $("#conversations-listview").css("display", "none");

            conversations.refresh(function(err) {
                if (err === null) {
                    var conversationsData: Conversation[] = conversations.get();
                    if (conversationsData !== null) {
                        $("#conversations-listview").empty();

                        var counter = 0;
                        async.eachSeries(conversationsData, function(conversation, asyncCallback: () => void) {
                            contacts.getContact(null, conversation.getRemotePhoneNumber(), function(contact) {
                                var messages = conversation.messages;

                                var listviewItem = $("<li>");
                                var listviewItemLink = $("<a id=\"conversations-listview-item-" + counter + "\" " +
                                "data-item-type=\"conversations-listview-item\" href=\"#\">");
                                listviewItem.append(listviewItemLink);

                                if (contact !== null && contact.photos !== null && contact.photos.length > 0) {
                                    $.get(contact.photos[0].value, function() {
                                        listviewItemLink.append("<img class=\"thumbnail\" src=\"" +
                                        contact.photos[0].value + "\">");
                                        completeEntry();
                                    }).fail(function() {
                                        listviewItemLink.append("<img src=\"images/placeholder.png\">");
                                        completeEntry();
                                    })
                                }
                                else {
                                    listviewItemLink.append("<img src=\"images/placeholder.png\">");
                                    completeEntry();
                                }

                                function completeEntry() {
                                    if (contact !== null && contact.displayName != null) {
                                        listviewItemLink.append("<h2>" + contact.displayName + "</h2>");
                                    }
                                    else {
                                        listviewItemLink.append("<h2>" + conversation.getRemotePhoneNumber() + "</h2>");
                                    }

                                    listviewItemLink.append("<p>" +
                                    conversation.messages[conversation.messages.length - 1].text + "</p>");

                                    var date = messages[messages.length - 1].date;
                                    var dateOuter = $("<p class=\"ui-li-aside date-time-container\">");
                                    var dateInner = $("<span class=\"date-time\">");
                                    if (date.isSame(moment().utc(), "day")) {
                                        dateInner.append(date.local().format("h:mm A"));
                                    }
                                    else if (date.isSame(moment().utc(), "year")) {
                                        dateInner.append(date.local().format("MMM D"));
                                    }
                                    else {
                                        dateInner.append(moment(date).local().format("YY/MM/DD"));
                                    }
                                    dateOuter.append(dateInner);
                                    listviewItemLink.append(dateOuter);

                                    var unreadMessages = 0;
                                    for (var i = 0; i < messages.length; i++) {
                                        if (messages[i].unread) {
                                            unreadMessages++;
                                        }
                                    }

                                    if (unreadMessages > 0) {
                                        listviewItemLink.append("<p class=\"ui-li-count unread-count\">" +
                                        unreadMessages + "</p>");
                                    }

                                    $("#conversations-listview").append(listviewItem);
                                    counter += 1;
                                    asyncCallback();
                                }
                            });
                        }, function() {
                            $("#conversations-listview").listview("refresh");
                            final();
                        });
                    }
                    else {
                        final();
                    }
                }
                else {
                    final();
                }

                function final() {
                    $("#conversations-listview").css("display", "");
                    ui.loading.stopLoading();

                    $.mobile.silentScroll(0);

                    $("a[data-item-type=\"conversations-listview-item\"]").on("click", function() {
                        if ($(this).attr("id").substr(0, 28) === "conversations-listview-item-") {
                            var selectedConversation: Conversation = conversations.get()[
                                parseInt($(this).attr("id").substring(28))];
                            var phoneNumber = selectedConversation.getRemotePhoneNumber();
                            showConversation(phoneNumber);
                        }
                    });

                    if (ui.notifications.notificationClicked) {
                        ui.notifications.processNotificationClick();
                    }

                    if (callback !== null) {
                        callback();
                    }
                }

                if (err !== null) {
                    ui.notifications.showToastNotification(err);
                }
            });
        }
    }

    export module loading {
        /**
         * Whether the application is currently loading.
         * @type {boolean}
         */
        var loading: boolean = false;

        /**
         * Returns whether the application is currently loading.
         * @returns {boolean} Whether the application is currently loading.
         */
        export function isLoading() {
            return loading;
        }

        /**
         * Displays the application's loading UI.
         */
        export function startLoading(): void {
            if (!loading) {
                $("body").pagecontainer("getActivePage").addClass('ui-disabled');
                $.mobile.loading("show", {
                    textVisible: true
                });

                loading = true;
            }
        }

        /**
         * Hides the application's loading UI.
         */
        export function stopLoading(): void {
            if (loading) {
                $("body").pagecontainer("getActivePage").removeClass('ui-disabled');
                $.mobile.loading("hide");

                loading = false;
            }
        }
    }

    export module menu {
        /**
         * Initializes the application's main menu.
         */
        export function initialize() {
            $(document).on("menubutton", function() {
                if (!ui.loading.isLoading()) {
                    if ($("body").pagecontainer("getActivePage").attr("id") === "conversations-page") {
                        $("menu").panel("toggle");
                    }
                }
            });

            $(".menu-button").on("click", function() {
                $("#menu").panel("toggle");
            });

            $("#menu-refresh-button").on("click", function() {
                ui.conversationspage.display();
            });

            $("#menu-mark-all-read-button").on("click", function() {
                conversations.markAllAsRead();
                ui.conversationspage.display();
            });
        }
    }

    export module main {
        /**
         * Initializes the application's main UI.
         */
        export function initialize() {
            $(document).on("backbutton", function() {
                if (!ui.loading.isLoading()) {
                    history.back();
                }
            });

            $(".button-back").on("click", function() {
                history.back();
            });

            $(".external-link").on("click", function() {
                var link = $(this).attr("data-link");
                window.open(link, "_system");
            });
        }
    }

    export module newconversationpage {
        /**
         * Initializes the application's new conversation dialog.
         */
        export function initialize() {
            $("body").on("pagecontainerchange", function(event, ui) {
                if (ui.toPage.attr("id") === "new-conversation-page") {
                    $("#new-conversation-recipient-input").val("");
                    $("#new-conversation-message-textarea").val("");
                }
            });

            $("#new-conversation-contacts-button").on("click", function() {
                window.plugins.ContactChooser.chooseContact(function (contactData) {
                    if (contactData.phoneNumber === "") {
                        ui.notifications.showToastNotification("Selected contact does not have a phone number.");
                    }
                    else {
                        var filteredPhoneNumber = contactData.phoneNumber;
                        filteredPhoneNumber = filteredPhoneNumber.replace(/[^\d]/g, "");
                        filteredPhoneNumber = filteredPhoneNumber.replace(/^.*(\d{10})$/, "$1");

                        $("#new-conversation-recipient-input").val(filteredPhoneNumber);
                    }
                });
            });

            $("#new-conversation-send-button").on("click", function() {
                var textarea = $("#new-conversation-message-textarea");

                var filteredPhoneNumber = $("#new-conversation-recipient-input").val().replace(/[^\d]/g, "").replace(
                    /^.*(\d{10})$/, "$1");
                if (filteredPhoneNumber.length !== 10) {
                    ui.notifications.showToastNotification("Phone number is not valid.");
                }
                else if (textarea.val().length > 160) {
                    ui.notifications.showToastNotification("Message exceeds 160 characters.");
                }
                else if (textarea.val() === "") {
                    ui.notifications.showToastNotification("Message is empty.");
                }
                else {
                    Api.sendSms(settings.getUsername(), settings.getPassword(), settings.getLocalPhoneNumber(),
                        filteredPhoneNumber, textarea.val(),
                        function(successful: boolean, err: string) {
                            if (successful) {
                                history.back();
                            } else {
                                ui.notifications.showToastNotification(err);
                            }
                        }
                    );
                }
            });
        }
    }

    export module notifications {
        /**
         * Whether a notification has been clicked but not been processed.
         * @type {boolean}
         */
        export var notificationClicked: boolean = false;

        /**
         * The phone number associated with an unprocessed clicked notification.
         * @type {string}
         */
        var notificationPhoneNumber: string = null;

        /**
         * Initializes the application's notifications UI.
         */
        export function initialize() {
            window.plugin.notification.local.oncancel = function(id, state, json) {
                notificationClicked = true;
                notificationPhoneNumber = JSON.parse(json);
                if ($("body").pagecontainer("getActivePage").attr("id") !== "conversations-page") {
                    history.back();
                }
                else {
                    ui.conversationspage.display();
                }
            };
        }

        /**
         * Processes a notification click.
         */
        export function processNotificationClick(): void {
            ui.conversationspage.showConversation(notificationPhoneNumber);
            notificationClicked = false;
        }

        /**
         * Shows a toast notification.
         * @param text The text of the notification.
         * @param type The type of the notification.
         * @param layout The location of the notification.
         */
        export function showToastNotification(text: string, type: string = "error", layout: string = "bottom"): void {
            var notification = noty({
                text: text,
                type: type,
                layout: layout
            });
            notification.show();
        }

        /**
         * Shows a status bar notification.
         * @param id The ID of the notification.
         * @param title The title of the notification.
         * @param text The text of the notification.
         * @param badgeValue The value of the number badge on the notification icon.
         * @param json Data associated with the notification.
         */
        export function showStatusBarNotification(id: number, title: String, text: String, badgeValue: number,
                                                  json: String): void {
            window.plugin.notification.local.add({
                id: String(id),
                title: title,
                message: text,
                badge: badgeValue,
                json: json,
                autoCancel: true
            });
        }

        /**
         * Hides the notification with the given ID.
         * @param id The ID of the notification to hide.
         */
        export function hideStatusBarNotification(id: number) {
            window.plugin.notification.local.cancel(id);
        }
    }

    export module settingspage {
        /**
         * Initializes the application's settings UI.
         */
        export function initialize() {
            $("body").on("pagecontainerchange", function(event, ui) {
                if (ui.toPage.attr("id") === "page-settings") {
                    $("#settings-username-input").val(settings.getUsername());
                    $("#settings-password-input").val(settings.getPassword());
                    $("#settings-phone-number-input").val(settings.getLocalPhoneNumber());
                    $("#settings-history-input").val(String(settings.getMessagesHistory()));
                    $("#settings-poll-rate-input").val(String(settings.getPollRate()));
                }
            });

            $("#settings-phone-numbers-button").on("click", function() {
                var phoneNumbersFieldset = $("#settings-phone-numbers-fieldset");

                Api.getLocalPhoneNumbers(settings.getUsername(), settings.getPassword(), function(phoneNumbers, err) {
                    if (err === null) {
                        if (phoneNumbers.length === 0) {
                            ui.notifications.showToastNotification("No phone numbers available. Is SMS enabled on " +
                            "the phone number you wish to use?");
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
                            $("#settings-phone-numbers-form").trigger("create");

                            $("body").pagecontainer("change", "#page-settings-phone-numbers");
                        }
                    }
                    else {
                        ui.notifications.showToastNotification(err);
                    }
                });
            });

            $("#settings-phone-numbers-select-button").on("click", function() {
                settings.setLocalPhoneNumber($('input:checked', '#settings-phone-numbers-fieldset').attr("name"));
                history.back();
            });

            $("#settings-save-button").on("click", function() {
                var historyInput = $("#settings-history-input");
                var pollRateInput = $("#settings-poll-rate-input");

                if (isNaN(historyInput.val()) || parseInt(historyInput.val()) <= 0 || historyInput.val() > 90) {
                    ui.notifications.showToastNotification("Number of days of SMS history to retrieve must be an " +
                        "integer greater than 0 and less than or equal to 90.");
                }
                else if (isNaN(pollRateInput.val()) || parseInt(pollRateInput.val()) < 0) {
                    ui.notifications.showToastNotification("SMS poll rate must be an integer greater than or equal " +
                        "to 0.");
                }
                else {
                    ui.notifications.showToastNotification("Settings saved.", "success");
                    settings.setUsername($("#settings-username-input").val());
                    settings.setPassword($("#settings-password-input").val());
                    settings.setMessagesHistory(historyInput.val());
                    settings.setPollRate(pollRateInput.val());
                }
            });
        }
    }
}