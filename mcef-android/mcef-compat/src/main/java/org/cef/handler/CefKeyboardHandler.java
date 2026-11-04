package org.cef.handler;

import org.cef.browser.CefBrowser;
import org.cef.event.CefKeyEvent;

public interface CefKeyboardHandler {
    default boolean onPreKeyEvent(CefBrowser browser, CefKeyEvent event) { return false; }
    default boolean onKeyEvent(CefBrowser browser, CefKeyEvent event) { return false; }
}
