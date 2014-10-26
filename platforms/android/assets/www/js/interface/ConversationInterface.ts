/// <reference path="../_references.ts" />

class ConversationInterface {
    public static initialize() {
        $("body").on("pagecontainerchange", function(event, ui) {
            if (ui.toPage.attr("id") === "page-conversation") {
                $.mobile.silentScroll(ui.toPage.height());
                $("#conversation-message-input").val("");
            }
        });

        $("#conversation-send-message-button").on("click", function() {
            if ($("#conversation-message-input").val().length > 160) {
                MainInterface.showToastNotification("Message exceeds 160 characters.");
            }
            else if ($("#conversation-message-input").val() === "") {
                MainInterface.showToastNotification("Message is empty.");
            }
            else {
                Api.sendSms(Settings.getUsername(), Settings.getPassword(), Settings.getLocalPhoneNumber(),
                    Conversations.getConversations()[Conversations.getActiveConversationIndex()].getRemotePhoneNumber(),
                    $("#conversation-message-input").val(), function(successful: boolean, err: string) {
                        if (successful) {
                            ConversationsInterface.displayConversations(function() {
                                ConversationInterface.displayConversation(
                                    Conversations.getConversations()[Conversations.getActiveConversationIndex()]);
                            });
                        } else {
                            MainInterface.showToastNotification(err);
                        }
                    }
                );
            }
        });
    }

    static displayConversation(conversation: Conversation) {
        MainInterface.showLoadingWidget();

        var header = $("#page-conversation-header");
        var listview = $("#conversation-listview");

        header.empty();
        listview.empty();

        Phone.getContact(null, conversation.messages[0].getRemotePhoneNumber(), function(contact) {
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
                        listviewItem.append($("<span>" + conversation.messages[i].getRemotePhoneNumber() + "</span>"));
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
            MainInterface.hideLoadingWidget();

            $.mobile.silentScroll($("#page-conversation").height());
        });
    }
}