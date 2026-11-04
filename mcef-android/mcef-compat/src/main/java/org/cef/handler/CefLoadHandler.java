package org.cef.handler;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;

public interface CefLoadHandler {
    enum ErrorCode { ERR_NONE, ERR_FAILED, ERR_ABORTED, ERR_TIMED_OUT, ERR_CONNECTION_REFUSED,
                     ERR_NAME_NOT_RESOLVED, ERR_INTERNET_DISCONNECTED, ERR_UNKNOWN }

    default void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                                      boolean canGoBack, boolean canGoForward) {}
    default void onLoadStart(CefBrowser browser, CefFrame frame, TransitionType type) {}
    default void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {}
    default void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode,
                             String errorText, String failedUrl) {}

    enum TransitionType { TT_LINK, TT_EXPLICIT, TT_AUTO_SUBFRAME, TT_MANUAL_SUBFRAME, TT_FORM_SUBMIT, TT_RELOAD }
}
