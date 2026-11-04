package org.cef.handler;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;

public class CefMessageRouterHandlerAdapter implements CefMessageRouterHandler {
    @Override
    public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
                           boolean persistent, CefQueryCallback callback) {
        return false;
    }

    @Override
    public void onQueryCanceled(CefBrowser browser, CefFrame frame, long queryId) { }
}
