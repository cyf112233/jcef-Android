package org.cef.browser;

/** 只保留主框架用得上的部分；OOPIF 子框架不在协议里。 */
public class CefFrame {
    private final CefBrowser browser;
    private final String url;

    public CefFrame(CefBrowser browser, String url) { this.browser = browser; this.url = url; }

    public CefBrowser getBrowser() { return browser; }
    public boolean isMain() { return true; }
    public boolean isValid() { return browser != null && !browser.isClosed(); }
    public String getURL() { return url; }
    public void loadURL(String u) { browser.loadURL(u); }
    public void executeJavaScript(String code, String scriptUrl, int startLine) {
        browser.executeJavaScript(code, scriptUrl, startLine);
    }
}
