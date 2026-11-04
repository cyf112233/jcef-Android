package org.cef.handler;

import org.cef.browser.CefBrowser;

public interface CefFocusHandler {
    default void onTakeFocus(CefBrowser browser, boolean next) {}
    default boolean onSetFocus(CefBrowser browser, int source) { return false; }
    default void onGotFocus(CefBrowser browser) {}
}
