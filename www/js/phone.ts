/// <reference path="_references.ts" />

/**
 * Interface to access information stored on the device.
 */
class Phone {
    /**
     * The local storage key for the contacts cache key.
     */
    private static CONTACTS_KEY: string = "contacts";

    /**
     * Refreshes the contacts cache.
     * @param callback A callback function with error information. The error parameter is null if the refresh was
     * successful.
     */
    public static refreshContacts(callback: (err: string) => void) {
        navigator.contacts.find(["*"], function(contacts) {
            window.localStorage.setItem(Phone.CONTACTS_KEY, JSON.stringify(contacts));
            callback(null);
        }, function() {
            callback("Error retrieving contacts from device.");
        });
    }

    /**
     * Retrieves the contact with the given display name or phone number. This function retrieves information from the
     * contacts cache.
     * @param displayName The display name to use for filtering.
     * @param phoneNumber The phone number to use for filtering.
     * @param callback A callback function to return the contact. The contact parameter of this function will be null
     * if no contact is found.
     */
    public static getContact(displayName: string, phoneNumber: string, callback: (Contact) => void): void {
        var contacts;

        function retrieveContacts() {
            contacts = JSON.parse(window.localStorage.getItem(Phone.CONTACTS_KEY));
            if (contacts === null) {
                Phone.refreshContacts(filterContacts);
                contacts = JSON.parse(window.localStorage.getItem(Phone.CONTACTS_KEY));
            }
            else {
                filterContacts();
            }
        }

        function filterContacts() {
            for (var j = 0; j < contacts.length; j++) {
                if (contacts[j].phoneNumbers !== null) {
                    for (var k = 0; k < contacts[j].phoneNumbers.length; k++) {
                        var contact: Contact = null;
                        var primaryMatch: boolean = displayName === null;
                        var secondaryMatch: boolean = phoneNumber === null;

                        if (displayName !== null) {
                            if (contacts[j].displayName === displayName) {
                                contact = contacts[j];
                                primaryMatch = true;
                            }
                        }
                        if (phoneNumber !== null) {
                            var filteredPhoneNumber = contacts[j].phoneNumbers[k].value.replace(/[^\d]/g, "").
                                replace(/^.*(\d{10})$/, "$1");
                            if (filteredPhoneNumber === phoneNumber) {
                                contact = contacts[j];
                                secondaryMatch = true;
                            }
                        }

                        if (primaryMatch && secondaryMatch) {
                            callback(contacts[j]);
                            return;
                        }
                    }
                }
            }

            callback(null);
        }

        retrieveContacts();
    }
}