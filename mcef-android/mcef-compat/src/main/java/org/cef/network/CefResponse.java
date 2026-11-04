package org.cef.network;

public class CefResponse {
    private int status;
    private String mimeType;
    private String statusText;

    public static CefResponse create() { return new CefResponse(); }

    public int getStatus() { return status; }
    public void setStatus(int s) { status = s; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String m) { mimeType = m; }
    public String getStatusText() { return statusText; }
    public void setStatusText(String t) { statusText = t; }
    public void setHeaderByName(String name, String value, boolean overwrite) { }

    @Override
    public String toString() { return status + " " + mimeType; }
}
