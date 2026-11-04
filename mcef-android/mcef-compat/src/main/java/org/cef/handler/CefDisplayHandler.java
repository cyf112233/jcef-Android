package org.cef.handler;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;

public interface CefDisplayHandler {
    default void onAddressChange(CefBrowser browser, CefFrame frame, String url) {}
    default void onTitleChange(CefBrowser browser, String title) {}
    default void onStatusMessage(CefBrowser browser, String value) {}
    default boolean onConsoleMessage(CefBrowser browser, int level, String message,
                                     String source, int line) { return false; }
    default boolean onCursorChange(CefBrowser browser, int cursorType) { return false; }
}
