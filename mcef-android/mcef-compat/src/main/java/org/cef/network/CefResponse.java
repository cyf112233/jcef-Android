package org.cef.network;

public class CefResponse {
    private int status;
    private String mimeType;

    public int getStatus() { return status; }
    public void setStatus(int s) { status = s; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String m) { mimeType = m; }
}
