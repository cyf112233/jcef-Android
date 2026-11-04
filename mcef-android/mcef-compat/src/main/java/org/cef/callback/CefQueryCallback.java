package org.cef.callback;

public interface CefQueryCallback {
    void success(String response);
    void failure(int errorCode, String errorMessage);
}
