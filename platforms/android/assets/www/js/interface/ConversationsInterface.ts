/// <reference path="../_references.ts" />

/**
 * Represents an interface for updating the conversations list UI.
 */
class ConversationsInterface {
    /**
     * Initializes the conversations interface.
     */
    public static initialize() {
        $("body").on("pagecontainerchange", function(event, ui) {
            if (ui.toPage.attr("id") === "page-conversations") {
                ConversationsInterface.displayConversations(null);
                $.mobile.silentScroll(0);
            }
        });

        ConversationsInterface.displayConversations(null, true);
    }

    public static displayConversations(callback: () => void = null, initial: Boolean = false) {
        MainInterface.showLoadingWidget();
        $("#conversations-listview").css("display", "none");

        Conversations.refreshConversations(function(err) {
            if (err !== null) {
                MainInterface.showToastNotification(err);
            }

            if (err === null || initial) {
                var conversations: Conversation[] = Conversations.getConversations();
                if (conversations !== null) {
                    $("#conversations-listview").empty();

                    var counter = 0;
                    async.eachSeries(conversations, function(conversation, asyncCallback: () => void) {
                        Phone.getContact(null, conversation.getRemotePhoneNumber(), function(contact) {
                            var messages = conversation.messages;

                            var listviewItem = $("<li>");
                            var listviewItemLink = $("<a id=\"conversations-listview-item-" + counter + "\" " +
                                "data-item-type=\"conversations-listview-item\" href=\"#\">");
                            listviewItem.append(listviewItemLink);

                            if (contact !== null && contact.photos !== null && contact.photos.length > 0) {
                                $.get(contact.photos[0].value, function() {
                                    listviewItemLink.append("<img class=\"thumbnail\" src=\"" + contact.photos[0].value + "\">");
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
                MainInterface.hideLoadingWidget();

                $.mobile.silentScroll(0);

                $("a[data-item-type=\"conversations-listview-item\"]").on("click", function() {
                    if ($(this).attr("id").substr(0, 28) === "conversations-listview-item-") {
                        var selectedConversationIndex: number = parseInt($(this).attr("id").substring(28));
                        var selectedConversation: Conversation = Conversations.getConversations()[
                            selectedConversationIndex];

                        Conversations.setActiveConversationIndex(selectedConversationIndex);

                        var conversations = Conversations.getConversations();
                        selectedConversation.markAllMessagesAsRead();
                        conversations[selectedConversationIndex] = selectedConversation;
                        Conversations.setConversations(conversations);

                        var conversationPage = $("#page-conversation");
                        $("body").pagecontainer("change", conversationPage);
                        ConversationInterface.displayConversation(selectedConversation);
                    }
                });

                if (callback !== null) {
                    callback();
                }
            }
        });
    }
}

