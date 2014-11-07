/// <reference path="_references.ts" />

/**
 * Fix lack of typing for notification plugin
 */
interface Window {
    plugin: any;
}

/**
 * Fix lack of typing for contact chooser plugin
 */
interface Plugins {
    ContactChooser: any;
}

/**
 * Fix incomplete typing for jQuery mobile
 */
interface JQuery {
    pagecontainer: any;
    panel: any
}