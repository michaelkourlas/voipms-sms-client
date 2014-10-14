/// <reference path="async/async.d.ts" />
/// <reference path="cordova/cordova.d.ts" />
/// <reference path="jquery/jquery.d.ts" />
/// <reference path="jquerymobile/jquerymobile.d.ts" />
/// <reference path="jquery.noty/jquery.noty.d.ts" />
/// <reference path="moment/moment.d.ts" />
var USERNAME_KEY = "username";
var PASSWORD_KEY = "password";
var CONVERSATIONS_KEY = "conversations";
var ACTIVE_CONVERSATION_KEY = "activeConversation";
var DEFAULT_LIMIT = 10000;
var LIMIT_INCREMENT = 1000;
var MAX_HISTORY = 90;
var Message = (function () {
    function Message(id, text, date, type, local, remote) {
        this.id = id;
        this.text = text;
        this.date = date;
        this.type = type;
        if (this.type = 0 /* Incoming */) {
            this.to = remote;
            this.from = local;
        }
        else if (this.type = 1 /* Outgoing */) {
            this.from = local;
            this.to = remote;
        }
    }
    Message.prototype.getLocal = function () {
        if (this.type === 0 /* Incoming */) {
            return this.to;
        }
        else if (this.type === 1 /* Outgoing */) {
            return this.from;
        }
    };
    Message.prototype.getRemote = function () {
        if (this.type === 0 /* Incoming */) {
            return this.from;
        }
        else if (this.type === 1 /* Outgoing */) {
            return this.to;
        }
    };
    return Message;
})();
var MessageType;
(function (MessageType) {
    MessageType[MessageType["Incoming"] = 0] = "Incoming";
    MessageType[MessageType["Outgoing"] = 1] = "Outgoing";
})(MessageType || (MessageType = {}));
var Conversation = (function () {
    function Conversation() {
        this.messages = [];
    }
    Conversation.prototype.addMessage = function (message) {
        if (this.messages.length == 0 || message.date.isAfter(this.messages[this.messages.length - 1].date)) {
            this.messages.push(message);
        }
        else {
            for (var i = 0; i < this.messages.length; i++) {
                if (message.date.isBefore(this.messages[i].date)) {
                    this.messages.splice(i, 0, message);
                }
            }
        }
    };
    Conversation.prototype.getStartDate = function () {
        return this.messages[0].date;
    };
    Conversation.prototype.getEndDate = function () {
        return this.messages[this.messages.length - 1].date;
    };
    Conversation.prototype.getLocal = function () {
        if (this.messages.length === 0) {
            return null;
        }
        else {
            return this.messages[0].getLocal();
        }
    };
    Conversation.prototype.getRemote = function () {
        if (this.messages.length === 0) {
            return null;
        }
        else {
            return this.messages[0].getRemote();
        }
    };
    return Conversation;
})();
var Conversations = (function () {
    function Conversations() {
    }
    Conversations.getConversations = function () {
        var conversationsJson = JSON.parse(window.localStorage.getItem(CONVERSATIONS_KEY));
        // Re-create object with functions from JSON
        var conversations = [];
        for (var i = 0; i < conversationsJson.length; i++) {
            conversations[i] = new Conversation();
            for (var j = 0; j < conversationsJson[i].messages.length; j++) {
                if (conversationsJson[i].messages[j].type == 0 /* Incoming */) {
                    conversations[i].addMessage(new Message(conversationsJson[i].messages[j].id, conversationsJson[i].messages[j].text, moment(conversationsJson[i].messages[j].date), conversationsJson[i].messages[j].type, conversationsJson[i].messages[j].to, conversationsJson[i].messages[j].from));
                }
                else if (conversationsJson[i].messages[j].type == 1 /* Outgoing */) {
                    conversations[i].addMessage(new Message(conversationsJson[i].messages[j].id, conversationsJson[i].messages[j].text, moment(conversationsJson[i].messages[j].date), conversationsJson[i].messages[j].type, conversationsJson[i].messages[j].from, conversationsJson[i].messages[j].to));
                }
            }
        }
        return conversations;
    };
    Conversations.refreshConversations = function (callback) {
        Conversations.getApiConversations(Settings.getUsername(), Settings.getPassword(), function (conversations, err) {
            if (conversations === null) {
                callback(err);
            }
            else {
                window.localStorage.setItem(CONVERSATIONS_KEY, JSON.stringify(conversations));
                callback(null);
            }
        });
    };
    Conversations.getApiConversations = function (username, password, callback) {
        // Continue making API requests until limit exceeds number of messages returned
        var limitArr = [DEFAULT_LIMIT];
        async.eachSeries(limitArr, function (limit, limitCallback) {
            var url = Conversations.createApiUrl(username, password, limit);
            var request = $.getJSON(url);
            request.done(function (data) {
                var conversations = Conversations.parseApiRequest(data);
                if (conversations != null) {
                    var messageCount = 0;
                    for (var i = 0; i < conversations.length; i++) {
                        messageCount += conversations[i].messages.length;
                    }
                    if (messageCount >= limit) {
                        limitArr.push(limit + LIMIT_INCREMENT);
                    }
                    else {
                        callback(conversations, null);
                    }
                }
                else {
                    callback(null, "Error decoding VoIP.ms API response. Are your username and password correct?");
                }
                limitCallback(null, null);
            });
            request.fail(function () {
                callback(null, "Error accessing VoIP.ms API. Are you connected to the Internet?");
                limitCallback(null, null);
            });
        }, function () {
            // Do nothing.
        });
    };
    Conversations.createApiUrl = function (username, password, limit) {
        var startDate = moment().subtract(MAX_HISTORY, "day");
        var endDate = moment();
        var voipUrl = "https://www.voip.ms/api/v1/rest.php?" + "&" + "api_username=" + encodeURIComponent(username) + "&" + "api_password=" + encodeURIComponent(password) + "&" + "method=getSMS" + "&" + "limit=" + encodeURIComponent(String(limit)) + "&" + "from=" + encodeURIComponent(startDate.utc().toISOString().substr(0, 10)) + "&" + "to=" + encodeURIComponent(endDate.utc().toISOString().substr(0, 10));
        var yqlCommand = "select * from json where url=\"" + voipUrl + "\"";
        var yqlUrl = "https://query.yahooapis.com/v1/public/yql?" + "q=" + encodeURIComponent(yqlCommand) + "&" + "format=json" + "&" + "callback=?";
        return yqlUrl;
    };
    Conversations.parseApiRequest = function (data) {
        try {
            var conversations = [];
            var rawMessages = data["query"]["results"]["json"]["sms"];
            if (!(rawMessages instanceof Array)) {
                rawMessages = [rawMessages];
            }
            for (var i = rawMessages.length - 1; i >= 0; i--) {
                var conversation = null;
                for (var j = 0; j < conversations.length; j++) {
                    if (conversations[j].getRemote() === rawMessages[i]["contact"]) {
                        conversation = conversations[j];
                    }
                }
                if (conversation === null) {
                    conversation = new Conversation();
                    conversations.push(conversation);
                }
                var message = new Message(parseInt(rawMessages[i]["id"]), rawMessages[i]["message"], moment.utc(rawMessages[i]["date"]), parseInt(rawMessages[i]["type"]) === 0 ? 1 /* Outgoing */ : 0 /* Incoming */, rawMessages[i]["did"], rawMessages[i]["contact"]);
                conversation.messages.push(message);
            }
            conversations.sort(function (a, b) {
                if (a.getEndDate().isSame(b.getEndDate())) {
                    return 0;
                }
                else if (a.getEndDate().isBefore(b.getEndDate())) {
                    return 1;
                }
                else {
                    return -1;
                }
            });
            return conversations;
        }
        catch (err) {
            return null;
        }
    };
    return Conversations;
})();
var Settings = (function () {
    function Settings() {
    }
    Settings.getUsername = function () {
        var username = window.localStorage.getItem(USERNAME_KEY);
        return username === null ? "" : username;
    };
    Settings.setUsername = function (username) {
        window.localStorage.setItem(USERNAME_KEY, username);
    };
    Settings.getPassword = function () {
        var password = window.localStorage.getItem(PASSWORD_KEY);
        return password === null ? "" : password;
    };
    Settings.setPassword = function (password) {
        window.localStorage.setItem(PASSWORD_KEY, password);
    };
    return Settings;
})();
var Interface = (function () {
    function Interface() {
    }
    Interface.getActiveConversation = function () {
        return Conversations.getConversations()[window.localStorage.getItem(ACTIVE_CONVERSATION_KEY)];
    };
    Interface.setActiveConversation = function (conversationId) {
        window.localStorage.setItem(ACTIVE_CONVERSATION_KEY, String(conversationId));
    };
    Interface.displayConversations = function (initial) {
        if (initial === void 0) { initial = false; }
        $.mobile.loading("show", {
            textVisible: true
        });
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
                    $("#listview-conversations").empty();
                    for (var i = 0; i < conversations.length; i++) {
                        var messages = conversations[i].messages;
                        var li = $("<li>");
                        var a = $("<a id=\"listview-item-" + i + "\" name=\"listview-item\" href=\"#page-conversation\">");
                        a.append("<img src=\"images/placeholder.png\">");
                        a.append("<h2>" + conversations[i].getRemote() + "</h2>");
                        a.append("<p>" + conversations[i].messages[conversations[i].messages.length - 1].text + "</p>");
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
                        a.append(dateOuter);
                        li.append(a);
                        $("#listview-conversations").append(li);
                    }
                    $("#listview-conversations").listview("refresh");
                }
            }
            $.mobile.loading("hide");
            $("a[name=\"listview-item\"]").on("click", function (event) {
                var itemId = $(event.currentTarget).attr("id");
                Interface.setActiveConversation(parseInt(itemId.substr(itemId.lastIndexOf("-") + 1)));
            });
        });
    };
    Interface.displayConversation = function (conversation) {
        $("#page-conversation-header").empty();
        $("#page-conversation-header").append(conversation.messages[0].getRemote());
        $("#listview-conversation").empty();
        for (var i = 0; i < conversation.messages.length; i++) {
            var li = $("<li>");
            if (conversation.messages[i].type === 1 /* Outgoing */) {
                li.append($("<span>You</span>"));
            }
            else if (conversation.messages[i].type === 0 /* Incoming */) {
                li.append($("<span>" + conversation.messages[i].getRemote() + "</span>"));
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
            li.append($("<p class=\"ui-li-aside label-datetime-outer\"><span class=\"label-datetime-inner\">" + date + "</span></p>"));
            li.append($("<br>"));
            li.append($("<p class=\"text-message\">" + conversation.messages[i].text + "</p>"));
            $("#listview-conversation").append(li);
        }
        $("#listview-conversation").listview("refresh");
    };
    return Interface;
})();
$(document).ready(function () {
    $("body").on("pagecontainerchange", function (event, ui) {
        if (ui.toPage.attr("id") === "page-conversation") {
            Interface.displayConversation(Interface.getActiveConversation());
        }
        else if (ui.toPage.attr("id") === "page-settings") {
            $("#textbox-settings-username").val(Settings.getUsername());
            $("#textbox-settings-password").val(Settings.getPassword());
        }
    });
    $(".button-menu").on("click", function () {
        $("#panel-menu").panel("close");
    });
    $("#button-menu-refresh").on("click", function () {
        Interface.displayConversations();
    });
    $("#button-settings-save").on("click", function () {
        Settings.setUsername($("#textbox-settings-username").val());
        Settings.setPassword($("#textbox-settings-password").val());
    });
    Interface.displayConversations(true);
});
//# sourceMappingURL=app.js.map