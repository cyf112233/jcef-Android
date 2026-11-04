package org.cef.browser;

import com.cinemamod.mcef.nativebridge.McefNative;
import org.cef.CefClient;
import org.cef.event.CefKeyEvent;
import org.cef.event.CefMouseEvent;
import org.cef.event.CefMouseWheelEvent;
import org.cef.handler.CefContextMenuHandler;
import org.cef.handler.CefDisplayHandler;
import org.cef.handler.CefDownloadHandler;
import org.cef.handler.CefFocusHandler;
import org.cef.handler.CefJSDialogHandler;
import org.cef.handler.CefKeyboardHandler;
import org.cef.handler.CefLifeSpanHandler;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefRenderHandler;

import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Off-screen rendered browser。
 *
 * <p>桌面 JCEF 里这个类走的是 {@code CefBrowser_N} → JNI；Android 上换成
 * {@link McefNative} → IPC → 插件 APK 里的真 libcef。事件从 native 的 rx 线程进来，
 * 所以实现里<b>不做 GL</b>，只把像素搬到调用方给的缓冲。</p>
 */
public class CefBrowserOsr extends CefBrowser implements CefRenderHandler, McefNative.Bridge {

    public interface JsCallback { void onResult(boolean ok, String result); }

    private static final AtomicInteger IDS = new AtomicInteger(1);

    private final int identifier = IDS.getAndIncrement();
    private final CefClient client;
    private final String initialUrl;
    private final boolean transparent;

    private volatile long handle;
    private volatile boolean closed;
    private volatile int width = 1280;
    private volatile int height = 720;
    private volatile float deviceScaleFactor = 1f;

    private volatile String url = "";
    private volatile String title = "";
    private volatile boolean loading;
    private volatile boolean canGoBack;
    private volatile boolean canGoForward;
    private volatile boolean focused;
    private volatile double zoomLevel = 1.0;

    private final ConcurrentHashMap<Integer, JsCallback> pendingJs = new ConcurrentHashMap<>();
    private final AtomicInteger jsIds = new AtomicInteger(1);

    public CefBrowserOsr(CefClient client, String url, boolean offscreen, boolean transparent) {
        this.client = client;
        this.initialUrl = url;
        this.transparent = transparent;
        this.url = url == null ? "" : url;
    }

    /* ---------- 生命周期 ---------- */

    /** 建连并创建浏览器。尺寸在 Cefrium 里是创建时固定的，所以先给默认值，后续 resize 会重发。 */
    public synchronized void createImmediately() {
        if (handle != 0L || closed) return;
        long h = McefNative.open();
        if (h == 0L) {
            McefNativeBridgeLog.warn("host 未启动，浏览器 " + identifier + " 保持挂起");
            return;
        }
        handle = h;
        McefNative.register(h, this);
        int rc = McefNative.create(h, width, height, deviceScaleFactor, transparent, initialUrl);
        if (rc != 0) {
            McefNative.unregister(h);
            handle = 0L;
        }
    }

    public void resize(int width, int height) {
        resize(width, height, deviceScaleFactor);
    }

    public void resize(int width, int height, float dsf) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.deviceScaleFactor = dsf;
        long h = handle;
        if (h != 0L) McefNative.nativeResize(h, this.width, this.height, dsf);
    }

    @Override
    public void close(boolean force) {
        closed = true;
        long h = handle;
        handle = 0L;
        if (h != 0L) McefNative.close(h);
    }

    @Override
    public boolean isClosed() { return closed; }

    @Override
    public int getIdentifier() { return identifier; }

    /* ---------- 导航 ---------- */

    @Override
    public void loadURL(String url) {
        if (url == null) return;
        this.url = url;
        long h = handle;
        if (h == 0L && !closed) {
            // host 可能是后起来的（插件 App 被系统回收后再打开），这里补一次
            createImmediately();
            h = handle;
        }
        if (h != 0L) McefNative.nativeLoadUrl(h, url);
    }

    @Override public void goBack() { long h = handle; if (h != 0L) McefNative.nativeGoBack(h); }
    @Override public void goForward() { long h = handle; if (h != 0L) McefNative.nativeGoForward(h); }
    @Override public void reload() { long h = handle; if (h != 0L) McefNative.nativeReload(h, false); }
    @Override public void reloadIgnoreCache() { long h = handle; if (h != 0L) McefNative.nativeReload(h, true); }
    @Override public void stopLoad() { long h = handle; if (h != 0L) McefNative.nativeStop(h); }

    @Override public String getURL() { return url; }
    @Override public String getTitle() { return title; }
    @Override public boolean isLoading() { return loading; }
    @Override public boolean canGoBack() { return canGoBack; }
    @Override public boolean canGoForward() { return canGoForward; }
    @Override public boolean hasDocument() { return !url.isEmpty(); }

    /* ---------- JS ---------- */

    @Override
    public void executeJavaScript(String code, String scriptUrl, int startLine) {
        long h = handle;
        if (h == 0L || code == null) return;
        McefNative.nativeEvalJs(h, 0, code);
    }

    public void executeJavaScript(String code, JsCallback callback) {
        long h = handle;
        if (h == 0L || code == null) return;
        int id = jsIds.getAndIncrement();
        if (callback != null) pendingJs.put(id, callback);
        McefNative.nativeEvalJs(h, id, code);
    }

    /* ---------- 视图属性 ---------- */

    @Override
    public void setZoomLevel(double level) {
        zoomLevel = level;
        long h = handle;
        if (h != 0L) McefNative.nativeZoom(h, level);
    }

    @Override public double getZoomLevel() { return zoomLevel; }

    @Override
    public void setFocus(boolean focus) {
        this.focused = focus;
        long h = handle;
        if (h != 0L) McefNative.nativeFocus(h, focus);
    }

    @Override public boolean isFocused() { return focused; }

    @Override public void setWindowlessFrameRate(int fps) { /* Cefrium 固定跟随 vsync */ }

    @Override
    public void setBackgroundColor(int argb) {
        long h = handle;
        if (h != 0L) McefNative.nativeBackground(h, argb);
    }

    /* ---------- 输入 ---------- */

    public void sendMouseMove(CefMouseEvent e, boolean leaving) {
        long h = handle;
        if (h != 0L) McefNative.nativeMouse(h, e.x, e.y, e.modifiers, 0, 0, leaving ? 3 : 0);
    }

    public void sendMousePress(CefMouseEvent e, int button, boolean down) {
        long h = handle;
        if (h == 0L) return;
        McefNative.nativeMouse(h, e.x, e.y, e.modifiers, button, 1, down ? 1 : 2);
    }

    public void sendMouseWheel(CefMouseWheelEvent e) {
        long h = handle;
        if (h != 0L) McefNative.nativeWheel(h, e.x, e.y, e.modifiers, e.deltaX, e.deltaY);
    }

    public void sendKeyEvent(CefKeyEvent e) {
        long h = handle;
        if (h == 0L) return;
        McefNative.nativeKey(h, e.type, e.windows_key_code, e.native_key_code,
                e.modifiers, e.character);
    }

    public void imeSetComposition(String text, int selectionStart, int selectionEnd, int caret) {
        long h = handle;
        if (h != 0L) McefNative.nativeIme(h, 1, text, selectionStart, selectionEnd, caret);
    }

    public void imeCommit(String text) {
        long h = handle;
        if (h != 0L) McefNative.nativeIme(h, 2, text, 0, 0, 0);
    }

    public void imeCancel() {
        long h = handle;
        if (h != 0L) McefNative.nativeIme(h, 3, null, 0, 0, 0);
    }

    /* ---------- McefNative.Bridge：native rx 线程 ---------- */

    @Override
    public void onPaint(int seq, int width, int height, ByteBuffer rgba) {
        // 子类（MCEFBrowser）覆盖；默认只把最后一帧丢掉
    }

    @Override
    public void onPopupPaint(int seq, int width, int height, ByteBuffer rgba) { }

    @Override
    public void onPopupShow(boolean show) {
        for (CefRenderHandler h : renderHandlers()) h.onPopupShow(this, show);
    }

    @Override
    public void onPopupSize(int x, int y, int width, int height) {
        for (CefRenderHandler h : renderHandlers()) h.onPopupSize(this, new Rectangle(x, y, width, height));
    }

    @Override
    public void onCursorChange(int cursorType) {
        for (CefRenderHandler h : renderHandlers()) h.onCursorChange(this, cursorType);
        for (CefDisplayHandler h : client.displayHandlers()) h.onCursorChange(this, cursorType);
    }

    @Override
    public void onLoadState(boolean loading, boolean canGoBack, boolean canGoForward) {
        this.loading = loading;
        this.canGoBack = canGoBack;
        this.canGoForward = canGoForward;
        CefFrame frame = new CefFrame(this, url);
        for (CefLoadHandler h : client.loadHandlers()) {
            h.onLoadingStateChange(this, loading, canGoBack, canGoForward);
            if (loading) h.onLoadStart(this, frame, CefLoadHandler.TransitionType.TT_LINK);
            else h.onLoadEnd(this, frame, 200);
        }
    }

    @Override
    public void onAddressChange(String url) {
        this.url = url;
        CefFrame frame = new CefFrame(this, url);
        for (CefDisplayHandler h : client.displayHandlers()) h.onAddressChange(this, frame, url);
    }

    @Override
    public void onTitleChange(String title) {
        this.title = title;
        for (CefDisplayHandler h : client.displayHandlers()) h.onTitleChange(this, title);
    }

    @Override
    public void onStatusMessage(String message) {
        for (CefDisplayHandler h : client.displayHandlers()) h.onStatusMessage(this, message);
    }

    @Override
    public void onConsoleMessage(int level, String message, String source, int line) {
        for (CefDisplayHandler h : client.displayHandlers()) h.onConsoleMessage(this, level, message, source, line);
    }

    @Override
    public void onBeforePopup(String url) {
        for (CefLifeSpanHandler h : client.lifeSpanHandlers()) h.onBeforePopup(this, url, "_blank");
    }

    @Override
    public void onJsResult(int id, boolean ok, String result) {
        JsCallback cb = id == 0 ? null : pendingJs.remove(id);
        if (cb != null) cb.onResult(ok, result);
    }

    @Override
    public void onAfterCreated(String url) {
        for (CefLifeSpanHandler h : client.lifeSpanHandlers()) h.onAfterCreated(this);
    }

    @Override
    public void onFocusedNode(boolean editable, String value, String placeholder, String type) { }

    @Override
    public void onSelectionChanged(String text) { }

    @Override
    public void onRenderTerminated(int status) {
        loading = false;
        McefNativeBridgeLog.warn("渲染进程终止 status=" + status + " browser=" + identifier);
    }

    @Override
    public void onDownloadBegin(String url, String suggestedName, long totalBytes) {
        for (CefDownloadHandler h : client.downloadHandlers())
            h.onBeforeDownload(this, suggestedName, url, totalBytes);
    }

    @Override
    public void onContextMenu(int x, int y, String linkUrl, String pageUrl, int type) {
        for (CefContextMenuHandler h : client.contextMenuHandlers())
            h.onBeforeContextMenu(this, x, y, linkUrl, pageUrl, type);
    }

    @Override
    public void onDisconnected() {
        McefNativeBridgeLog.warn("与 host 的连接断开，browser=" + identifier);
        closed = true;
    }

    /* ---------- CefRenderHandler ---------- */

    @Override
    public Rectangle getViewRect(CefBrowser browser) {
        return new Rectangle(0, 0, width, height);
    }

    /** Cefrium 给整帧，没有 dirty rect；保留给需要自行裁剪的调用方。 */
    public Rectangle[] getDirtyRects() { return new Rectangle[] { new Rectangle(0, 0, width, height) }; }

    protected java.util.List<CefRenderHandler> renderHandlers() {
        return java.util.Collections.singletonList(this);
    }

    protected CefClient getClient() { return client; }

    public long getHandle() { return handle; }

    /** 小日志门面，避免把 slf4j 硬拽进兼容层。 */
    static final class McefNativeBridgeLog {
        static void warn(String msg) {
            System.err.println("[MCEF-Android] " + msg);
        }
    }
}
