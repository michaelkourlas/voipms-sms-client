/// <reference path="../third-party/moment/moment.d.ts" />
/**
 * Represents the type of a message.
 */
var MessageType;
(function (MessageType) {
    /**
     * A message received by the local phone number.
     */
    MessageType[MessageType["Incoming"] = 0] = "Incoming";
    /**
     * A message sent by the local phone number.
     */
    MessageType[MessageType["Outgoing"] = 1] = "Outgoing";
})(MessageType || (MessageType = {}));
/**
 * Represents an SMS message.
 */
var Message = (function () {
    /**
     * Initializes a new instance of the Message class.
     * @param id The ID number associated with the message.
     * @param text The text of the message.
     * @param date The date the message was sent or received by the local phone number, as appropriate.
     * @param type The type of the message (incoming or outgoing).
     * @param local The phone number of the person currently using the application.
     * @param remote The phone number of the person not currently using the application.
     */
    function Message(id, text, date, type, local, remote) {
        this.id = id;
        this.text = text;
        this.date = date;
        this.type = type;
        if (this.type === 0 /* Incoming */) {
            this.to = local;
            this.from = remote;
        }
        else if (this.type === 1 /* Outgoing */) {
            this.to = remote;
            this.from = local;
        }
    }
    /**
     * Restores an original Message object (with functions) from an object containing only message data. These
     * data-only objects are the result of stringifying the original object, storing it in the browser's local storage,
     * then parsing the saved JSON at a later point.
     * @param savedObject An object containing only raw message data.
     * @returns {*} The original Message object, or null if the message data could not be parsed.
     */
    Message.createMessage = function (savedObject) {
        try {
            var message = null;
            if (savedObject.type == 0 /* Incoming */) {
                message = new Message(savedObject.id, savedObject.text, moment(savedObject.date), savedObject.type, savedObject.to, savedObject.from);
            }
            else if (savedObject.type == 1 /* Outgoing */) {
                message = new Message(savedObject.id, savedObject.text, moment(savedObject.date), savedObject.type, savedObject.from, savedObject.to);
            }
            return message;
        }
        catch (err) {
            return null;
        }
    };
    /**
     * Gets the local phone number associated with the message (i.e. the phone number of the person currently using
     * the application).
     * @returns {string} The local phone number.
     */
    Message.prototype.getLocalPhoneNumber = function () {
        if (this.type === 0 /* Incoming */) {
            return this.to;
        }
        else if (this.type === 1 /* Outgoing */) {
            return this.from;
        }
    };
    /**
     * Gets the remote phone number associated with the message (i.e. the phone number of the person not currently
     * using the application).
     * @returns {string} The remote phone number.
     */
    Message.prototype.getRemotePhoneNumber = function () {
        if (this.type === 0 /* Incoming */) {
            return this.from;
        }
        else if (this.type === 1 /* Outgoing */) {
            return this.to;
        }
    };
    /**
     * Returns whether two messages are equal.
     * @param message The second message.
     * @returns {boolean} Whether the two messages are equal.
     */
    Message.prototype.equals = function (message) {
        return this.id === message.id && this.text === message.text && this.date.isSame(message.date) && this.type === message.type && this.to === message.to && this.from === message.from;
    };
    return Message;
})();
//# sourceMappingURL=message.js.map