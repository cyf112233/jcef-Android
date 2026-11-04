package org.cef.handler;

import org.cef.browser.CefBrowser;

public interface CefContextMenuHandler {
    default void onBeforeContextMenu(CefBrowser browser, int x, int y,
                                     String linkUrl, String pageUrl, int type) {}
    default boolean onContextMenuCommand(CefBrowser browser, int commandId) { return false; }
}
