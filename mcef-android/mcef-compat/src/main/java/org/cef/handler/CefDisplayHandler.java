package org.cef.handler;

import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;

public interface CefDisplayHandler {
    default void onAddressChange(CefBrowser browser, CefFrame frame, String url) {}
    default void onTitleChange(CefBrowser browser, String title) {}
    default void onStatusMessage(CefBrowser browser, String value) {}

    /** 严重级别是 CefSettings.LogSeverity 枚举（上游签名），不是 int。 */
    default boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level,
                                     String message, String source, int line) { return false; }

    default boolean onCursorChange(CefBrowser browser, int cursorType) { return false; }
    default boolean onTooltip(CefBrowser browser, String text) { return false; }
}
