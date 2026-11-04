package org.cef.handler;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;

public interface CefContextMenuHandler {
    default void onBeforeContextMenu(CefBrowser browser, CefFrame frame,
                                     CefContextMenuParams params, CefMenuModel model) {}

    default boolean onContextMenuCommand(CefBrowser browser, CefFrame frame,
                                         CefContextMenuParams params, int commandId,
                                         int eventFlags) { return false; }

    default void onContextMenuDismissed(CefBrowser browser, CefFrame frame) {}
}
