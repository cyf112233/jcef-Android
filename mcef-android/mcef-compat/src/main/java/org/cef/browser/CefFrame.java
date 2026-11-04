package org.cef.browser;

/** 上游是接口。实现见 CefFrameImpl。 */
public interface CefFrame {
    void dispose();
    long getIdentifier();
    String getURL();
    String getName();
    boolean isMain();
    boolean isValid();
    boolean isFocused();
    CefFrame getParent();
    void executeJavaScript(String code, String scriptUrl, int startLine);
    void undo();
    void redo();
    void cut();
    void copy();
    void paste();
}
