package org.cef.callback;

/** 异步继续/取消。CEF 里用于资源读取和导航回调。 */
public interface CefCallback {
    void Continue();
    void cancel();
}
