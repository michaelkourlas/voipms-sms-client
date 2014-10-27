/// <reference path="_references.ts" />

/**
 * Fix lack of typing for de.appplant.cordova.plugin.local-notification plugin
 */
interface Window {
    plugin: any;
}

interface Plugins {
    ContactChooser: any;
}

/**
 * Fix problems with jquerymobile.d.ts
 */
interface JQuery {
    pagecontainer: any;
    panel: any
}