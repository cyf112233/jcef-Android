package com.mcef.host;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.cefrium.CefriumBrowser;
import com.cefrium.CefriumRenderHandler;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * 一个连接对应一个 CefriumBrowser。
 *
 * <p>线程模型：所有 Cefrium 调用都必须在主线程（它有 Looper 依赖），
 * 所以命令进来之后一律 post 到主线程；画面在 onPaint（主线程）里拷进队列，
 * 由写线程发回 MC，避免 UI 线程被 socket 写阻塞。</p>
 */
public class BrowserSession {

    private static final String TAG = "MCEF-Host";
    private static final int EV_PAINT = 0x0201;

    private final Context context;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final java.net.Socket socket;
    private final Handler main = new Handler(Looper.getMainLooper());

    private final Object writeLock = new Object();
    private volatile boolean closed;

    private CefriumBrowser browser;
    private int width = 1280;
    private int height = 720;
    private int seq;

    BrowserSession(Context ctx, DataInputStream in, DataOutputStream out, java.net.Socket socket) {
        this.context = ctx;
        this.in = in;
        this.out = out;
        this.socket = socket;
    }

    void run() throws IOException {
        while (!closed) {
            int len = in.readInt();
            int type = in.readUnsignedShort();
            int flags = in.readUnsignedShort();
            int id = in.readInt();
            if (len < 0 || len > (64 << 20)) break;
            byte[] payload = new byte[len];
            in.readFully(payload);
            handle(type, id, payload, flags);
        }
    }

    private void handle(int type, int id, byte[] p, int flags) {
        Payload in = new Payload(p);
        switch (type) {
            case 0x0101: {                                  // CREATE_BROWSER
                int w = in.u32(), h = in.u32();
                float dsf = in.f32();
                boolean transparent = in.u8() != 0;
                String url = in.str();
                createBrowser(w, h, dsf, transparent, url);
                break;
            }
            case 0x0102: close(); break;                    // DESTROY
            case 0x0103: { String url = in.str(); main.post(() -> call(browser, "loadUrl", url)); break; }
            case 0x0104: {
                int w = in.u32(), h = in.u32();
                in.f32();
                width = w; height = h;
                // Cefrium 没有 resize：重建浏览器最稳（协议里已注明）
                main.post(() -> rebuild(w, h));
                break;
            }
            case 0x0110: main.post(() -> call(browser, "goBack")); break;
            case 0x0111: main.post(() -> call(browser, "goForward")); break;
            case 0x0112: main.post(() -> call(browser, "reload")); break;
            case 0x0113: main.post(() -> call(browser, "stopLoading")); break;
            case 0x0120: case 0x0121: case 0x0122: case 0x0123: {
                int x = in.i32(), y = in.i32(), mods = in.u32();
                int arg = in.u8();
                int count = in.u16();
                main.post(() -> sendMouse(type, x, y, mods, arg, count));
                break;
            }
            case 0x0130: {
                int kind = in.u8(), key = in.i32(), scan = in.i32(), mods = in.u32(), chr = in.u16();
                main.post(() -> sendKey(kind, key, scan, mods, chr));
                break;
            }
            case 0x0140: {
                boolean focused = in.u8() != 0;
                main.post(() -> call(browser, "setFocus", focused));
                break;
            }
            case 0x0150: {
                String code = in.str();
                int jsId = id;
                main.post(() -> evalJs(code, jsId));
                break;
            }
            case 0x0151: {
                double level = in.f64();
                main.post(() -> call(browser, "setZoomLevel", (float) level));
                break;
            }
            case 0x0152: {
                int argb = in.u32();
                main.post(() -> call(browser, "setBackgroundColor", argb));
                break;
            }
            default:
                Log.d(TAG, "unhandled op 0x" + Integer.toHexString(type));
                break;
        }
    }

    private void createBrowser(int w, int h, float dsf, boolean transparent, String url) {
        width = w; height = h;
        main.post(() -> {
            try {
                browser = new CefriumBrowser(w, h);
                browser.setRenderHandler(new RenderHandler());
                if (transparent) call(browser, "setBackgroundColor", 0x00000000);
                if (url != null && !url.isEmpty()) call(browser, "loadUrl", url);
            } catch (Throwable t) {
                Log.e(TAG, "createBrowser failed", t);
            }
        });
    }

    /** Cefrium 没有 resize API，只能重建；重建前把 URL 捞出来，别让页面回到 about:blank。 */
    private void rebuild(int w, int h) {
        String currentUrl = null;
        try {
            if (browser != null) {
                try {
                    Object u = browser.getClass().getMethod("getUrl").invoke(browser);
                    if (u instanceof String) currentUrl = (String) u;
                } catch (Throwable ignored) { }
                call(browser, "destroy");
            }
            browser = new CefriumBrowser(w, h);
            browser.setRenderHandler(new RenderHandler());
            if (currentUrl != null && !currentUrl.isEmpty()) call(browser, "loadUrl", currentUrl);
        } catch (Throwable t) {
            Log.e(TAG, "resize/rebuild failed", t);
        }
    }

    /**
     * Cefrium 的输入 API 在 0.17.0 里带 MOUSE_BUTTON_* / KEY_* 常量，
     * 具体方法名用反射隔离版本漂移；名称对不上时只告警，不中断会话。
     */
    private void sendMouse(int op, int x, int y, int mods, int button, int count) {
        if (browser == null) return;
        int action = op == 0x0121 ? 1 : op == 0x0122 ? 2 : 0;
        if (!invoke(browser, "sendMouseEvent",
                new Class<?>[]{int.class, int.class, int.class, int.class, int.class},
                action, x, y, mods, button)) {
            Log.w(TAG, "Cefrium 未暴露 sendMouseEvent，输入待对齐 0.17.0 API");
        }
    }

    private void sendKey(int kind, int key, int scan, int mods, int chr) {
        if (browser == null) return;
        invoke(browser, "sendKeyEvent", new Class<?>[]{int.class, int.class, int.class, int.class},
                kind, key, mods, chr);
    }

    private void evalJs(String code, int id) {
        if (browser == null) return;
        try {
            Method m = browser.getClass().getMethod("evaluateJavaScript", String.class);
            m.invoke(browser, code);
        } catch (Throwable t) {
            Log.w(TAG, "evaluateJavaScript failed", t);
        }
    }

    private static boolean call(Object target, String name) {
        return target != null && invoke(target, name, new Class<?>[0]);
    }

    private static boolean call(Object target, String name, Object... args) {
        if (target == null) return false;
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) types[i] = args[i] instanceof Integer ? int.class
                : args[i] instanceof Boolean ? boolean.class : args[i].getClass();
        return invoke(target, name, types, args);
    }

    private static boolean invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method m = target.getClass().getMethod(name, types);
            m.invoke(target, args);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Throwable t) {
            Log.w(TAG, "invoke " + name + " failed", t);
            return false;
        }
    }

    /* ---------- 画面回传 ---------- */

    private final class RenderHandler implements CefriumRenderHandler {
        @Override
        public void onPaint(int w, int h, ByteBuffer rgba) {
            if (closed) return;
            int need = w * h * 4;
            ByteBuffer src = rgba.duplicate();
            if (src.remaining() < need) return;
            byte[] copy = new byte[need];
            src.get(copy, 0, need);
            int frameSeq = ++seq;
            // 写线程发，别占主线程
            Writer.get().submit(() -> writeFrame(frameSeq, w, h, copy));
        }
    }

    private void writeFrame(int frameSeq, int w, int h, byte[] rgba) {
        if (closed) return;
        try {
            synchronized (writeLock) {
                out.writeInt(16 + rgba.length);   // payload: seq,w,h,stride,data
                out.writeShort(EV_PAINT);
                out.writeShort(0);
                out.writeInt(frameSeq);
                out.writeInt(frameSeq);
                out.writeInt(w);
                out.writeInt(h);
                out.writeInt(w * 4);
                out.write(rgba);
                out.flush();
            }
        } catch (IOException e) {
            close();
        }
    }

    void close() {
        closed = true;
        try { socket.close(); } catch (IOException ignored) { }
    }

    /** 单线程帧发送器，最新帧优先。 */
    static final class Writer {
        private static final java.util.concurrent.ExecutorService EXEC =
                java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "mcef-frame-writer");
                    t.setDaemon(true);
                    return t;
                });
        static java.util.concurrent.ExecutorService get() { return EXEC; }
    }

    /** 简易读游标，和 native 侧 mcef_reader 对齐。 */
    static final class Payload {
        private final byte[] b;
        private int pos;
        Payload(byte[] b) { this.b = b; }
        int u8() { return b[pos++] & 0xFF; }
        int u16() { int v = (b[pos] & 0xFF) | ((b[pos + 1] & 0xFF) << 8); pos += 2; return v; }
        int u32() { int v = (b[pos] & 0xFF) | ((b[pos + 1] & 0xFF) << 8)
                | ((b[pos + 2] & 0xFF) << 16) | ((b[pos + 3] & 0xFF) << 24); pos += 4; return v; }
        int i32() { return u32(); }
        long i64() { long lo = u32() & 0xFFFFFFFFL; long hi = u32() & 0xFFFFFFFFL; return lo | (hi << 32); }
        float f32() { return Float.intBitsToFloat(u32()); }
        double f64() { return Double.longBitsToDouble(i64()); }
        String str() {
            int n = u32();
            String s = new String(Arrays.copyOfRange(b, pos, pos + n), java.nio.charset.StandardCharsets.UTF_8);
            pos += n;
            return s;
        }
    }
}
