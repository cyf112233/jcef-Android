package org.cef.browser;

import org.cef.CefClient;
import org.cef.handler.CefRenderHandler;

/**
 * 上游是 interface，不是 class —— 消费方的字节码是 invokeinterface，
 * 写成 class 会直接 IncompatibleClassChangeError。
 */
public interface CefBrowser {

    void createImmediately();

    CefClient getClient();

    CefRenderHandler getRenderHandler();

    boolean canGoBack();
    void goBack();
    boolean canGoForward();
    void goForward();
    boolean isLoading();
    void reload();
    void reloadIgnoreCache();
    void stopLoad();

    int getIdentifier();
    boolean isClosed();
    void setWindowlessFrameRate(int fps);
    CefFrame getMainFrame();
    CefFrame getFocusedFrame();
    boolean hasDocument();

    void loadURL(String url);
    String getURL();
    String getTitle();

    void executeJavaScript(String code, String url, int line);

    void close(boolean force);
    default void close() { close(true); }
    void setCloseAllowed();
    boolean doClose();

    void setZoomLevel(double level);
    double getZoomLevel();

    void setFocus(boolean focus);
    boolean isFocused();

    void resize(int width, int height);
    void setBackgroundColor(int argb);
}
