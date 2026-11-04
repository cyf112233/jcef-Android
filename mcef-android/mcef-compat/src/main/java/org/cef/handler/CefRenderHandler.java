package org.cef.handler;

import org.cef.browser.CefBrowser;

import java.awt.Rectangle;
import java.nio.ByteBuffer;

public interface CefRenderHandler {
    default Rectangle getViewRect(CefBrowser browser) { return new Rectangle(0, 0, 1, 1); }
    default void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects,
                         ByteBuffer buffer, int width, int height) {}
    default void onPopupShow(CefBrowser browser, boolean show) {}
    default void onPopupSize(CefBrowser browser, Rectangle size) {}
    default boolean onCursorChange(CefBrowser browser, int cursorType) { return true; }
}
