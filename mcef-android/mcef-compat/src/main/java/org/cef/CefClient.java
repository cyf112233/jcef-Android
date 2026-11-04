package org.cef;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowserOsr;
import org.cef.handler.CefContextMenuHandler;
import org.cef.handler.CefDisplayHandler;
import org.cef.handler.CefDownloadHandler;
import org.cef.handler.CefDragHandler;
import org.cef.handler.CefFocusHandler;
import org.cef.handler.CefJSDialogHandler;
import org.cef.handler.CefKeyboardHandler;
import org.cef.handler.CefLifeSpanHandler;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefRenderHandler;

import java.util.concurrent.CopyOnWriteArrayList;

/** 一个客户端可以挂多个浏览器；handler 列表这里维护，事件由 CefBrowserOsr 分发。 */
public class CefClient {

    private final CopyOnWriteArrayList<CefLoadHandler> loadHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CefDisplayHandler> displayHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CefLifeSpanHandler> lifeSpanHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CefJSDialogHandler> jsDialogHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CefDownloadHandler> downloadHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CefContextMenuHandler> contextMenuHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CefKeyboardHandler> keyboardHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CefFocusHandler> focusHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CefDragHandler> dragHandlers = new CopyOnWriteArrayList<>();

    public CefBrowser createBrowser(String url, boolean offscreen, boolean transparent) {
        return new CefBrowserOsr(this, url, offscreen, transparent);
    }

    public CefBrowser createBrowser(String url, boolean offscreen) {
        return createBrowser(url, offscreen, false);
    }

    public void addLoadHandler(CefLoadHandler h) { if (h != null) loadHandlers.add(h); }
    public void removeLoadHandler(CefLoadHandler h) { loadHandlers.remove(h); }
    public void addDisplayHandler(CefDisplayHandler h) { if (h != null) displayHandlers.add(h); }
    public void removeDisplayHandler(CefDisplayHandler h) { displayHandlers.remove(h); }
    public void addLifeSpanHandler(CefLifeSpanHandler h) { if (h != null) lifeSpanHandlers.add(h); }
    public void removeLifeSpanHandler(CefLifeSpanHandler h) { lifeSpanHandlers.remove(h); }
    public void addJSDialogHandler(CefJSDialogHandler h) { if (h != null) jsDialogHandlers.add(h); }
    public void removeJSDialogHandler(CefJSDialogHandler h) { jsDialogHandlers.remove(h); }
    public void addDownloadHandler(CefDownloadHandler h) { if (h != null) downloadHandlers.add(h); }
    public void removeDownloadHandler(CefDownloadHandler h) { downloadHandlers.remove(h); }
    public void addContextMenuHandler(CefContextMenuHandler h) { if (h != null) contextMenuHandlers.add(h); }
    public void removeContextMenuHandler(CefContextMenuHandler h) { contextMenuHandlers.remove(h); }
    public void addKeyboardHandler(CefKeyboardHandler h) { if (h != null) keyboardHandlers.add(h); }
    public void removeKeyboardHandler(CefKeyboardHandler h) { keyboardHandlers.remove(h); }
    public void addFocusHandler(CefFocusHandler h) { if (h != null) focusHandlers.add(h); }
    public void removeFocusHandler(CefFocusHandler h) { focusHandlers.remove(h); }
    public void addDragHandler(CefDragHandler h) { if (h != null) dragHandlers.add(h); }
    public void removeDragHandler(CefDragHandler h) { dragHandlers.remove(h); }

    public java.util.List<CefLoadHandler> loadHandlers() { return loadHandlers; }
    public java.util.List<CefDisplayHandler> displayHandlers() { return displayHandlers; }
    public java.util.List<CefLifeSpanHandler> lifeSpanHandlers() { return lifeSpanHandlers; }
    public java.util.List<CefJSDialogHandler> jsDialogHandlers() { return jsDialogHandlers; }
    public java.util.List<CefDownloadHandler> downloadHandlers() { return downloadHandlers; }
    public java.util.List<CefContextMenuHandler> contextMenuHandlers() { return contextMenuHandlers; }
    public java.util.List<CefKeyboardHandler> keyboardHandlers() { return keyboardHandlers; }
    public java.util.List<CefFocusHandler> focusHandlers() { return focusHandlers; }
    public java.util.List<CefDragHandler> dragHandlers() { return dragHandlers; }

    public void dispose() {
        loadHandlers.clear(); displayHandlers.clear(); lifeSpanHandlers.clear();
        jsDialogHandlers.clear(); downloadHandlers.clear(); contextMenuHandlers.clear();
        keyboardHandlers.clear(); focusHandlers.clear(); dragHandlers.clear();
    }
}
