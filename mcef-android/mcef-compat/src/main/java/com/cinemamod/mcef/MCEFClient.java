package com.cinemamod.mcef;

import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandler;
import org.cef.handler.CefLoadHandler;
import org.cef.network.CefRequest;

import java.util.concurrent.CopyOnWriteArrayList;

/** CefClient 的包装，转发 load/display 事件给使用方。 */
public class MCEFClient implements CefLoadHandler, CefDisplayHandler {

    private final CefClient handle;
    private final CopyOnWriteArrayList<CefLoadHandler> loadListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CefDisplayHandler> displayListeners = new CopyOnWriteArrayList<>();

    public MCEFClient(CefClient cefClient) {
        this.handle = cefClient;
        cefClient.addLoadHandler(this);
        cefClient.addDisplayHandler(this);
    }

    public CefClient getHandle() { return handle; }

    public void addLoadHandler(CefLoadHandler handler) {
        if (handler != null) { loadListeners.add(handler); handle.addLoadHandler(handler); }
    }

    public void addDisplayHandler(CefDisplayHandler handler) {
        if (handler != null) { displayListeners.add(handler); handle.addDisplayHandler(handler); }
    }

    @Override
    public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                                     boolean canGoBack, boolean canGoForward) {
        for (CefLoadHandler h : loadListeners)
            h.onLoadingStateChange(browser, isLoading, canGoBack, canGoForward);
    }

    @Override
    public void onLoadStart(CefBrowser browser, CefFrame frame, TransitionType type) {
        for (CefLoadHandler h : loadListeners) h.onLoadStart(browser, frame, type);
    }

    @Override
    public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
        for (CefLoadHandler h : loadListeners) h.onLoadEnd(browser, frame, httpStatusCode);
    }

    @Override
    public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode,
                            String errorText, String failedUrl) {
        for (CefLoadHandler h : loadListeners) h.onLoadError(browser, frame, errorCode, errorText, failedUrl);
    }

    @Override
    public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
        for (CefDisplayHandler h : displayListeners) h.onAddressChange(browser, frame, url);
    }

    @Override
    public void onTitleChange(CefBrowser browser, String title) {
        for (CefDisplayHandler h : displayListeners) h.onTitleChange(browser, title);
    }

    @Override
    public void onStatusMessage(CefBrowser browser, String value) {
        for (CefDisplayHandler h : displayListeners) h.onStatusMessage(browser, value);
    }

    @Override
    public boolean onConsoleMessage(CefBrowser browser, int level, String message,
                                    String source, int line) {
        boolean handled = false;
        for (CefDisplayHandler h : displayListeners)
            handled |= h.onConsoleMessage(browser, level, message, source, line);
        return handled;
    }

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        boolean handled = false;
        for (CefDisplayHandler h : displayListeners) handled |= h.onCursorChange(browser, cursorType);
        return handled;
    }
}
