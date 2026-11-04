package org.cef.handler;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.network.CefRequest;

public interface CefRequestHandler {
    default boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request,
                                   boolean isRedirect) { return false; }
    default boolean onCertificateError(CefBrowser browser, int certError, String requestUrl) {
        return false;
    }
}
