package com.cinemamod.mcef.nativebridge;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * libmcef.so 的 JNI 绑定。
 *
 * <p>约束：这个类（以及整个 mcef-compat 模块）<b>不能引用任何 android.*</b> ——
 * MC 跑在纯 OpenJDK JVM 里，那些类加载不到。</p>
 *
 * <p>一进程一条连接 = 一个浏览器会话，所以 handle 直接就是 session 指针。</p>
 */
public final class McefNative {

    /** 事件回调目标。CefBrowserOsr 实现它，java 侧不关心 IPC。 */
    public interface Bridge {
        void onPaint(int seq, int width, int height, ByteBuffer rgba);
        void onPopupPaint(int seq, int width, int height, ByteBuffer rgba);
        void onPopupShow(boolean show);
        void onPopupSize(int x, int y, int width, int height);
        void onCursorChange(int cefCursorType);
        void onLoadState(boolean loading, boolean canGoBack, boolean canGoForward);
        void onAddressChange(String url);
        void onTitleChange(String title);
        void onStatusMessage(String message);
        void onConsoleMessage(int level, String message, String source, int line);
        void onBeforePopup(String url);
        void onJsResult(int id, boolean ok, String result);
        void onAfterCreated(String url);
        void onFocusedNode(boolean editable, String value, String placeholder, String type);
        void onSelectionChanged(String text);
        void onRenderTerminated(int status);
        void onDownloadBegin(String url, String suggestedName, long totalBytes);
        void onContextMenu(int x, int y, String linkUrl, String pageUrl, int type);
        void onDisconnected();
    }

    private static final ConcurrentHashMap<Long, Bridge> BRIDGES = new ConcurrentHashMap<>();

    private static volatile boolean loaded;
    private static volatile String loadError;

    static {
        try {
            System.loadLibrary("mcef");
            loaded = true;
        } catch (Throwable first) {
            // FCL 的 NativeLibPlugin 会把插件 APK 的 nativeLibraryDir 拼进 LD_LIBRARY_PATH，
            // 正常情况下上面这句就够了。下面的 -Dmcef.libdir 是兜底：
            // 插件 meta-data 里声明 environment="-Dmcef.libdir={nativeLibraryDir}"。
            String dir = System.getProperty("mcef.libdir");
            if (dir != null && !dir.isEmpty()) {
                try {
                    System.load(dir + "/libmcef.so");
                    loaded = true;
                    return;
                } catch (Throwable second) {
                    loadError = second.getMessage();
                    return;
                }
            }
            loadError = first.getMessage();
        }
    }

    private McefNative() {}

    public static boolean isLoaded() { return loaded; }
    public static String loadError() { return loadError; }

    public static long open() {
        if (!loaded) return 0L;
        return nativeConnect();
    }

    public static void register(long handle, Bridge bridge) {
        if (handle != 0L) BRIDGES.put(handle, bridge);
    }

    public static void unregister(long handle) {
        if (handle != 0L) BRIDGES.remove(handle);
    }

    public static void close(long handle) {
        if (handle == 0L) return;
        BRIDGES.remove(handle);
        try {
            nativeClose(handle);
        } catch (Throwable ignored) {
            // host 已经挂了
        }
    }

    /* ---------- JNI ---------- */

    private static native long nativeConnect();
    private static native void nativeClose(long h);
    private static native int  nativeCreate(long h, int w, int height, float dsf, boolean transparent, String url);

    public static native void nativeDestroy(long h);
    public static native void nativeLoadUrl(long h, String url);
    public static native void nativeResize(long h, int w, int height, float dsf);
    public static native void nativeGoBack(long h);
    public static native void nativeGoForward(long h);
    public static native void nativeReload(long h, boolean ignoreCache);
    public static native void nativeStop(long h);
    public static native void nativeMouse(long h, int x, int y, int mods, int button, int count, int action);
    public static native void nativeWheel(long h, int x, int y, int mods, int dx, int dy);
    public static native void nativeKey(long h, int kind, int key, int scan, int mods, int chr);
    public static native void nativeFocus(long h, boolean focused);
    public static native void nativeIme(long h, int kind, String text, int a, int b, int c);
    public static native void nativeEvalJs(long h, int id, String code);
    public static native void nativeZoom(long h, double level);
    public static native void nativeBackground(long h, int argb);
    public static native boolean nativePing(long h);

    /** 建浏览器。返回 0 表示成功。 */
    public static int create(long handle, int width, int height, float dsf, boolean transparent, String url) {
        return nativeCreate(handle, width, height, dsf, transparent, url);
    }

    /* ---------- 由 native 的 rx 线程回调（勿改名/改签名） ---------- */

    private static Bridge bridge(long h) { return BRIDGES.get(h); }

    static void onPaint(long h, int seq, int w, int height, ByteBuffer buf) {
        Bridge b = bridge(h);
        if (b != null) b.onPaint(seq, w, height, buf);
    }

    static void onPopupPaint(long h, int seq, int w, int height, ByteBuffer buf) {
        Bridge b = bridge(h);
        if (b != null) b.onPopupPaint(seq, w, height, buf);
    }

    static void onPopupShow(long h, boolean show) {
        Bridge b = bridge(h);
        if (b != null) b.onPopupShow(show);
    }

    static void onPopupSize(long h, int x, int y, int w, int height) {
        Bridge b = bridge(h);
        if (b != null) b.onPopupSize(x, y, w, height);
    }

    static void onCursorChange(long h, int type) {
        Bridge b = bridge(h);
        if (b != null) b.onCursorChange(type);
    }

    static void onLoadState(long h, boolean loading, boolean back, boolean fwd) {
        Bridge b = bridge(h);
        if (b != null) b.onLoadState(loading, back, fwd);
    }

    static void onAddressChange(long h, String url) {
        Bridge b = bridge(h);
        if (b != null) b.onAddressChange(url);
    }

    static void onTitleChange(long h, String title) {
        Bridge b = bridge(h);
        if (b != null) b.onTitleChange(title);
    }

    static void onStatusMessage(long h, String msg) {
        Bridge b = bridge(h);
        if (b != null) b.onStatusMessage(msg);
    }

    static void onConsoleMessage(long h, int level, String msg, String src, int line) {
        Bridge b = bridge(h);
        if (b != null) b.onConsoleMessage(level, msg, src, line);
    }

    static void onBeforePopup(long h, String url) {
        Bridge b = bridge(h);
        if (b != null) b.onBeforePopup(url);
    }

    static void onJsResult(long h, int id, boolean ok, String result) {
        Bridge b = bridge(h);
        if (b != null) b.onJsResult(id, ok, result);
    }

    static void onAfterCreated(long h, String url) {
        Bridge b = bridge(h);
        if (b != null) b.onAfterCreated(url);
    }

    static void onFocusedNode(long h, boolean editable, String value, String placeholder, String type) {
        Bridge b = bridge(h);
        if (b != null) b.onFocusedNode(editable, value, placeholder, type);
    }

    static void onSelectionChanged(long h, String text) {
        Bridge b = bridge(h);
        if (b != null) b.onSelectionChanged(text);
    }

    static void onRenderTerminated(long h, int status) {
        Bridge b = bridge(h);
        if (b != null) b.onRenderTerminated(status);
    }

    static void onDownloadBegin(long h, String url, String name, long total) {
        Bridge b = bridge(h);
        if (b != null) b.onDownloadBegin(url, name, total);
    }

    static void onContextMenu(long h, int x, int y, String link, String page, int type) {
        Bridge b = bridge(h);
        if (b != null) b.onContextMenu(x, y, link, page, type);
    }

    static void onDisconnected(long h) {
        Bridge b = bridge(h);
        if (b != null) b.onDisconnected();
    }
}
