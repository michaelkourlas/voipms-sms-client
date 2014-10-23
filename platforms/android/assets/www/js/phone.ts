/// <reference path="../third-party/cordova/cordova.d.ts" />

class Phone {
    static getContact(phoneNumber: String, callback: (Contact) => void): void {
        navigator.contacts.find(["displayName, photos, phoneNumbers"], function(contacts) {
            if (contacts !== null) {
                for (var j = 0; j < contacts.length; j++) {
                    if (contacts[j].phoneNumbers !== null) {
                        for (var k = 0; k < contacts[j].phoneNumbers.length; k++) {
                            var filteredPhoneNumber = contacts[j].phoneNumbers[k].value.replace(/[^\d]/g, "").
                                replace(/^.*(\d{10})$/, "$1");
                            if (filteredPhoneNumber === phoneNumber) {
                                callback(contacts[j]);
                                return;
                            }
                        }
                    }
                }
            }
            callback(null);
        }, function() {
            callback(null);
        });
    }
}