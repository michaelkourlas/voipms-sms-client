#import "ContactChooser.h"
#import <Cordova/CDVAvailability.h>

@implementation ContactChooser
@synthesize callbackID;

- (void) chooseContact:(CDVInvokedUrlCommand*)command{
    self.callbackID = command.callbackId;
    
    ABPeoplePickerNavigationController *picker = [[ABPeoplePickerNavigationController alloc] init];
    picker.peoplePickerDelegate = self;
    [self.viewController presentModalViewController:picker animated:YES];
}

- (BOOL)peoplePickerNavigationController:(ABPeoplePickerNavigationController*)peoplePicker
      shouldContinueAfterSelectingPerson:(ABRecordRef)person
                                property:(ABPropertyID)property
                              identifier:(ABMultiValueIdentifier)identifier
{
    if (kABPersonEmailProperty == property)
    {
        ABMultiValueRef multi = ABRecordCopyValue(person, kABPersonEmailProperty);
        int index = ABMultiValueGetIndexForIdentifier(multi, identifier);
        NSString *email = (__bridge NSString *)ABMultiValueCopyValueAtIndex(multi, index);
        NSString *displayName = (__bridge NSString *)ABRecordCopyCompositeName(person);
        ABMultiValueRef multiPhones = ABRecordCopyValue(person, kABPersonPhoneProperty);
        NSString* phoneNumber = @"";
        for(CFIndex i = 0; i < ABMultiValueGetCount(multiPhones); i++) {
            if(identifier == ABMultiValueGetIdentifierAtIndex (multiPhones, i)) {
                phoneNumber = (__bridge NSString *)ABMultiValueCopyValueAtIndex(multiPhones, i);
                break;
            }
        }

        NSMutableDictionary* contact = [NSMutableDictionary dictionaryWithCapacity:2];
        [contact setObject:email forKey: @"email"];
        [contact setObject:displayName forKey: @"displayName"];
        [contact setObject:phoneNumber forKey: @"phoneNumber"];

        [super writeJavascript:[[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:contact] toSuccessCallbackString:self.callbackID]];
        [self.viewController dismissModalViewControllerAnimated:YES];
        return NO;
    }
    return YES;
}

- (BOOL) personViewController:(ABPersonViewController*)personView shouldPerformDefaultActionForPerson:(ABRecordRef)person property:(ABPropertyID)property identifier:(ABMultiValueIdentifier)identifierForValue
{
    return YES;
}

- (void)peoplePickerNavigationControllerDidCancel:(ABPeoplePickerNavigationController *)peoplePicker{
    [self.viewController dismissModalViewControllerAnimated:YES];
    [super writeJavascript:[[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                              messageAsString:@"People picker abort"]
                                            toErrorCallbackString:self.callbackID]];
}

@end
