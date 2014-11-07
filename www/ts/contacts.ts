/// <reference path="_references.ts" />

/**
 * Interface to access contacts information stored on the device.
 */
module contacts {
    /**
     * The local storage key for the contacts cache key.
     * @type string
     */
    var CONTACTS_KEY: string = "contacts";

    /**
     * Refreshes the contacts cache.
     * @param callback A callback function with error information. The error parameter is null if the refresh was
     * successful.
     */
    export function refreshContacts(callback: (err: string) => void) {
        navigator.contacts.find(["*"], function(contacts) {
            window.localStorage.setItem(CONTACTS_KEY, JSON.stringify(contacts));
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
    export function getContact(displayName: string, phoneNumber: string, callback: (Contact) => void): void {
        var contactsData;

        function retrieveContacts() {
            contactsData = JSON.parse(window.localStorage.getItem(CONTACTS_KEY));
            if (contactsData === null) {
                refreshContacts(filterContacts);
                contactsData = JSON.parse(window.localStorage.getItem(CONTACTS_KEY));
            }
            else {
                filterContacts();
            }
        }

        function filterContacts() {
            for (var j = 0; j < contactsData.length; j++) {
                if (contactsData[j].phoneNumbers !== null) {
                    for (var k = 0; k < contactsData[j].phoneNumbers.length; k++) {
                        var contactData: Contact = null;
                        var primaryMatch: boolean = displayName === null;
                        var secondaryMatch: boolean = phoneNumber === null;

                        if (displayName !== null) {
                            if (contactsData[j].displayName === displayName) {
                                contactData = contactsData[j];
                                primaryMatch = true;
                            }
                        }
                        if (phoneNumber !== null) {
                            var filteredPhoneNumber = contactsData[j].phoneNumbers[k].value;
                            filteredPhoneNumber = filteredPhoneNumber.replace(/[^\d]/g, "");
                            filteredPhoneNumber = filteredPhoneNumber.replace(/^.*(\d{10})$/, "$1");

                            if (filteredPhoneNumber === phoneNumber) {
                                contactData = contactsData[j];
                                secondaryMatch = true;
                            }
                        }

                        if (primaryMatch && secondaryMatch) {
                            callback(contactsData[j]);
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