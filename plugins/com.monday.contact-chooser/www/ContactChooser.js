/**
 * @constructor
 */
var ContactChooser = function(){};


ContactChooser.prototype.chooseContact = function(success, failure){
    cordova.exec(success, failure, "ContactChooser", "chooseContact", []);
};

// Plug in to Cordova
cordova.addConstructor(function() {

    if (!window.Cordova) {
        window.Cordova = cordova;
    };


    if(!window.plugins) window.plugins = {};
    window.plugins.ContactChooser = new ContactChooser();
});
