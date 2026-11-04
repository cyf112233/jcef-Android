package org.cef.handler;

import org.cef.browser.CefBrowser;

public interface CefJSDialogHandler {
    default boolean onJSDialog(CefBrowser browser, String originUrl, int dialogType,
                               String messageText, String defaultPromptText, String[] outResult) {
        return false;
    }
    default boolean onBeforeUnloadDialog(CefBrowser browser, String messageText,
                                         boolean isReload, String[] outResult) {
        return false;
    }
}
