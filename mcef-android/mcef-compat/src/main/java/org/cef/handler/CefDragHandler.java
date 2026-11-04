package org.cef.handler;

import org.cef.browser.CefBrowser;
import org.cef.callback.CefDragData;

public interface CefDragHandler {
    default boolean onDragEnter(CefBrowser browser, CefDragData dragData, int mask) { return false; }
}
