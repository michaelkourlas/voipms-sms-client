/// <reference path="../third-party/jquery/jquery.d.ts" />
/// <reference path="../third-party/jquerymobile/jquerymobile.d.ts" />
/// <reference path="../third-party/jquery.noty/jquery.noty.d.ts" />
/// <reference path="conversations.ts" />
/// <reference path="phone.ts" />
var Interface = (function () {
    function Interface() {
    }
    Interface.displayConversations = function (initial) {
        if (initial === void 0) { initial = false; }
        Interface.showLoadingWidget();
        $("#conversations-listview").css("display", "none");
        Conversations.refreshConversations(function (err) {
            if (err !== null) {
                var notification = noty({
                    text: err,
                    type: "error",
                    layout: "bottom"
                });
                notification.show();
            }
            if (err === null || initial) {
                var conversations = Conversations.getConversations();
                if (conversations != null) {
                    $("#conversations-listview").empty();
                    var counter = 0;
                    async.eachSeries(conversations, function (conversation, callback) {
                        Phone.getContact(conversation.getRemotePhoneNumber(), function (contact) {
                            var messages = conversation.messages;
                            var listviewItem = $("<li>");
                            var listviewItemLink = $("<a id=\"conversations-listview-item-" + counter + "\" " + "name=\"conversations-listview-item\" href=\"#\">");
                            listviewItem.append(listviewItemLink);
                            if (contact != null) {
                                if (contact.photos !== null && contact.photos.length > 0) {
                                    $.get(contact.photos[0].value).done(function () {
                                        listviewItemLink.append("<img src=\"" + contact.photos[0].value + "\">");
                                    }).fail(function () {
                                        listviewItemLink.append("<img src=\"images/placeholder.png\">");
                                    }).always(function () {
                                        if (contact.displayName != null) {
                                            listviewItemLink.append("<h2>" + contact.displayName + "</h2>");
                                        }
                                        else {
                                            listviewItemLink.append("<h2>" + conversation.getRemotePhoneNumber() + "</h2>");
                                        }
                                        continueFunction();
                                    });
                                }
                                else {
                                    listviewItemLink.append("<img src=\"images/placeholder.png\">");
                                    if (contact.displayName != null) {
                                        listviewItemLink.append("<h2>" + contact.displayName + "</h2>");
                                    }
                                    else {
                                        listviewItemLink.append("<h2>" + conversation.getRemotePhoneNumber() + "</h2>");
                                    }
                                    continueFunction();
                                }
                            }
                            else {
                                listviewItemLink.append("<img src=\"images/placeholder.png\">");
                                listviewItemLink.append("<h2>" + conversation.getRemotePhoneNumber() + "</h2>");
                                continueFunction();
                            }
                            function continueFunction() {
                                listviewItemLink.append("<p>" + conversation.messages[conversation.messages.length - 1].text + "</p>");
                                var date = messages[messages.length - 1].date;
                                var dateOuter = $("<p class=\"ui-li-aside label-datetime-outer\">");
                                var dateInner = $("<span class=\"label-datetime-inner\">");
                                if (moment(date).isSame(moment(), "day")) {
                                    dateInner.append(moment(date).format("h:mm A"));
                                }
                                else if (moment(date).isSame(moment(), "year")) {
                                    dateInner.append(moment(date).format("MMM D"));
                                }
                                else {
                                    dateInner.append(moment(date).format("YY/MM/DD"));
                                }
                                dateOuter.append(dateInner);
                                listviewItemLink.append(dateOuter);
                                $("#conversations-listview").append(listviewItem);
                                counter += 1;
                                callback();
                            }
                        });
                    }, function () {
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
                Interface.hideLoadingWidget();
                $("a[name=\"conversations-listview-item\"]").on("click", function () {
                    if ($(this).attr("id").substr(0, 28) === "conversations-listview-item-") {
                        var conversationPage = $("#page-conversation");
                        //noinspection TaskProblemsInspection
                        $("body").pagecontainer("change", conversationPage);
                        Interface.displayConversation(Conversations.getConversations()[parseInt($(this).attr("id").substring(28))]);
                    }
                });
                window.plugin.notification.local.add({
                    id: "test",
                    message: "Hello, world"
                });
                window.plugin.notification.local.add({
                    id: "test2",
                    message: "Hello, world2"
                });
            }
        });
    };
    Interface.displayConversation = function (conversation) {
        Interface.showLoadingWidget();
        var header = $("#page-conversation-header");
        var listview = $("#conversation-listview");
        header.empty();
        listview.empty();
        Phone.getContact(conversation.messages[0].getRemotePhoneNumber(), function (contact) {
            if (contact !== null && contact.displayName !== null) {
                header.append(contact.displayName);
            }
            else {
                header.append(conversation.messages[0].getRemotePhoneNumber());
            }
            for (var i = 0; i < conversation.messages.length; i++) {
                var listviewItem = $("<li>");
                if (conversation.messages[i].type === 1 /* Outgoing */) {
                    listviewItem.append($("<span>You</span>"));
                }
                else if (conversation.messages[i].type === 0 /* Incoming */) {
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
                listviewItem.append($("<p class=\"ui-li-aside label-datetime-outer\">" + "<span class=\"label-datetime-inner\">" + date + "</span></p>"));
                listviewItem.append($("<br>"));
                listviewItem.append($("<p class=\"text-message\">" + conversation.messages[i].text + "</p>"));
                listview.append(listviewItem);
            }
            listview.listview("refresh");
            Interface.hideLoadingWidget();
        });
    };
    Interface.showLoadingWidget = function () {
        $.mobile.loading("show", {
            textVisible: true
        });
    };
    Interface.hideLoadingWidget = function () {
        $.mobile.loading("hide");
    };
    return Interface;
})();
//# sourceMappingURL=interface.js.map