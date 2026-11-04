package org.cef.handler;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.network.CefRequest;

public interface CefLoadHandler {

    enum ErrorCode {
        ERR_NONE, ERR_FAILED, ERR_ABORTED, ERR_TIMED_OUT, ERR_CONNECTION_REFUSED,
        ERR_NAME_NOT_RESOLVED, ERR_INTERNET_DISCONNECTED, ERR_UNKNOWN
    }

    default void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                                      boolean canGoBack, boolean canGoForward) {}

    /** 注意：类型是 CefRequest.TransitionType，不是本接口内的枚举（上游如此）。 */
    default void onLoadStart(CefBrowser browser, CefFrame frame,
                             CefRequest.TransitionType transitionType) {}

    default void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {}

    default void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode,
                             String errorText, String failedUrl) {}
}
