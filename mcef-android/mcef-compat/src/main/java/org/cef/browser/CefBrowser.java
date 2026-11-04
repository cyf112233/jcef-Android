package org.cef.browser;

/** 浏览器句柄。桌面 JCEF 里它还有 UI 组件相关的方法，这里只保留 OSR 用得到的。 */
public abstract class CefBrowser {

    public abstract void loadURL(String url);
    public abstract void goBack();
    public abstract void goForward();
    public abstract void reload();
    public abstract void reloadIgnoreCache();
    public abstract void stopLoad();

    public abstract String getURL();
    public abstract String getTitle();
    public abstract boolean isLoading();
    public abstract boolean canGoBack();
    public abstract boolean canGoForward();
    public abstract boolean hasDocument();

    public abstract void executeJavaScript(String code, String url, int line);
    public abstract void setZoomLevel(double level);
    public abstract double getZoomLevel();

    public abstract void setFocus(boolean focus);
    public abstract boolean isFocused();

    public abstract void setWindowlessFrameRate(int fps);
    public abstract int getIdentifier();
    public abstract boolean isClosed();

    public abstract void resize(int width, int height);
    public abstract void setBackgroundColor(int argb);

    public abstract void close(boolean force);

    /** 上游还有 close()，以及立即创建、取客户端这些入口。 */
    public void close() { close(true); }
    public abstract void createImmediately();
    public abstract void setCloseAllowed();
    public abstract org.cef.CefClient getClient();
}
