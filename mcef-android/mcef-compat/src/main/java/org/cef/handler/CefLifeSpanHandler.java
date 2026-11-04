package org.cef.handler;

import org.cef.browser.CefBrowser;

public interface CefLifeSpanHandler {
    default void onAfterCreated(CefBrowser browser) {}
    default boolean doClose(CefBrowser browser) { return false; }
    default void onBeforeClose(CefBrowser browser) {}
    default boolean onBeforePopup(CefBrowser browser, String targetUrl, String targetFrameName) {
        return false;
    }
}
