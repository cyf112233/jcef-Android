package org.cef.handler;

import org.cef.browser.CefBrowser;

public interface CefDownloadHandler {
    default void onBeforeDownload(CefBrowser browser, String suggestedName,
                                  String url, long totalBytes) {}
    default void onDownloadUpdated(CefBrowser browser, String path,
                                   long received, long total, boolean complete) {}
}
