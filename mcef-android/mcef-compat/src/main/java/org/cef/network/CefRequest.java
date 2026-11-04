package org.cef.network;

import java.util.Collections;
import java.util.Map;

public class CefRequest {
    public enum TransitionType { TT_LINK, TT_EXPLICIT, TT_FORM_SUBMIT, TT_RELOAD }

    private final String url;
    private final String method;
    private final Map<String, String> headers;

    public CefRequest(String url, String method, Map<String, String> headers) {
        this.url = url; this.method = method;
        this.headers = headers == null ? Collections.emptyMap() : headers;
    }
    public String getURL() { return url; }
    public void setURL(String u) { /* 协议里改 URL 走 loadURL */ }
    public String getMethod() { return method; }
    public Map<String, String> getHeaderMap() { return headers; }
}
