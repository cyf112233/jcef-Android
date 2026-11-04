package org.cef.browser;

/** 单框架实现：Android 侧没有 OOPIF，所有帧都是主框架。 */
public class CefFrameImpl implements CefFrame {

    private final CefBrowser browser;
    private final String url;

    public CefFrameImpl(CefBrowser browser, String url) {
        this.browser = browser;
        this.url = url == null ? "" : url;
    }

    @Override public void dispose() { }
    @Override public long getIdentifier() { return 0L; }
    @Override public String getURL() { return url; }
    @Override public String getName() { return ""; }
    @Override public boolean isMain() { return true; }
    @Override public boolean isValid() { return browser != null && !browser.isClosed(); }
    @Override public boolean isFocused() { return browser != null && browser.isFocused() && isMain(); }
    @Override public CefFrame getParent() { return null; }

    @Override
    public void executeJavaScript(String code, String scriptUrl, int startLine) {
        if (browser != null) browser.executeJavaScript(code, scriptUrl, startLine);
    }

    @Override public void undo() { }
    @Override public void redo() { }
    @Override public void cut() { }
    @Override public void copy() { }
    @Override public void paste() { }

    /** 本实现特有：方便内部按 URL 重新加载。 */
    public void loadURL(String u) { if (browser != null) browser.loadURL(u); }
}
