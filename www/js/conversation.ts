/// <reference path="../third-party/moment/moment.d.ts" />
/// <reference path="message.ts" />

/**
 * Represents a collection of SMS messages between the same two individuals.
 */
class Conversation {
    /**
     * The SMS messages that make up the conversation.
     */
    messages: Message[];

    /**
     * Initializes a new instance of the Conversation class.
     */
    constructor() {
        this.messages = [];
    }

    /**
     * Restores an original Conversation object (with functions) from an object containing only conversation data. These
     * data-only objects are the result of stringifying the original object, storing it in the browser's local storage,
     * then parsing the saved JSON at a later point.
     * @param savedObject An object containing only raw conversation data.
     * @returns {*} The original Conversation object, or null if the message data could not be parsed.
     */
    static createConversation(savedObject: any): Conversation {
        try {
            var conversation: Conversation = new Conversation();
            for (var i = 0; i < savedObject.messages.length; i++) {
                var message: Message = Message.createMessage(savedObject.messages[i]);
                conversation.addMessage(message);
            }
            return conversation;
        }
        catch (err) {
            return null;
        }
    }

    /**
     * Adds a message to the conversation. Messages are added in order, from oldest at the beginning of the messages
     * array to newest at the end of the messages array.
     * @param message The message to add to the conversation.
     */
    addMessage(message: Message) : void {
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
    }

    /**
     * Gets the start date of the conversation.
     * @returns {Moment} The start date of the conversation.
     */
    getStartDate(): Moment {
        return this.messages[0].date;
    }

    /**
     * Gets the end date of the conversation.
     * @returns {Moment} The end date of the conversation.
     */
    getEndDate(): Moment {
        return this.messages[this.messages.length - 1].date;
    }

    /**
     * Gets the local phone number associated with the conversation (i.e. the phone number of the person currently
     * using the application).
     * @returns {string} The local phone number, or null if the messages array is empty.
     */
    getLocalPhoneNumber(): string {
        if (this.messages.length === 0) {
            return null;
        }
        else {
            return this.messages[0].getLocalPhoneNumber();
        }
    }

    /**
     * Gets the remote phone number associated with the conversation (i.e. the phone number of the person not
     * currently using the application).
     * @returns {string} The remote phone number, or null if the messages array is empty.
     */
    getRemotePhoneNumber(): string {
        if (this.messages.length === 0) {
            return null;
        }
        else {
            return this.messages[0].getRemotePhoneNumber();
        }
    }

    /**
     * Returns whether two conversations are equal.
     * @param message The second conversation.
     * @returns {boolean} Whether the two conversations are equal.
     */
    equals(conversation: Conversation): boolean {
        if (this.messages.length !== conversation.messages.length) {
            return false;
        }

        for (var i = 0; i < this.messages.length; i++) {
            if (!this.messages[i].equals(conversation.messages[i])) {
                return false;
            }
        }

        return true;
    }
}