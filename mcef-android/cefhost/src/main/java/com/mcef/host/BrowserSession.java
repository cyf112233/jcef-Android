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
    private final java.util.Map<Long, Object> pendingQueries = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Integer, java.util.concurrent.CompletableFuture<SchemeReply>> pendingSchemes =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger schemeReqIds =
            new java.util.concurrent.atomic.AtomicInteger(1);
    private final java.io.ByteArrayOutputStream schemeBody = new java.io.ByteArrayOutputStream();
    private final java.util.concurrent.atomic.AtomicLong queryIds = new java.util.concurrent.atomic.AtomicLong(1);
    private volatile Object queryHandlerProxy;
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
            case 0x0240: case 0x0241:  // 这两个是 host→mc 的方向，收到说明协议用反了
                Log.w(TAG, "unexpected op 0x" + Integer.toHexString(type));
                break;
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
            case 0x0160: {                                  // QUERY_REPLY
                long qid = in.i64();
                boolean ok = in.u8() != 0;
                int code = in.i32();
                String response = in.str();
                Object cb = pendingQueries.remove(qid);
                if (cb != null) main.post(() -> deliverQueryResult(cb, ok, code, response));
                break;
            }
            case 0x0161: {                                  // SCHEME_RESPONSE
                int reqId = in.u32();
                boolean found = in.u8() != 0;
                int status = in.i32();
                String mime = in.str();
                int len = in.u32();
                byte[] body = in.bytes(len);
                java.util.concurrent.CompletableFuture<SchemeReply> f = pendingSchemes.remove(reqId);
                if (f != null) f.complete(new SchemeReply(found, status, mime, body));
                break;
            }
            default:
                Log.d(TAG, "unhandled op 0x" + Integer.toHexString(type));
                break;
        }
    }

    /** 自定义协议资源请求的回包。 */
    static final class SchemeReply {
        final boolean found;
        final int status;
        final String mime;
        final byte[] body;
        SchemeReply(boolean found, int status, String mime, byte[] body) {
            this.found = found; this.status = status;
            this.mime = mime; this.body = body == null ? new byte[0] : body;
        }
    }

    /** 由 HttpBridge 的线程调用；等 MC 把 CefResourceHandler 的内容送回来。 */
    SchemeReply fetchSchemeResource(String url, String method) {
        int reqId = schemeReqIds.getAndIncrement();
        java.util.concurrent.CompletableFuture<SchemeReply> future = new java.util.concurrent.CompletableFuture<>();
        pendingSchemes.put(reqId, future);

        java.io.ByteArrayOutputStream payload = new java.io.ByteArrayOutputStream();
        try {
            payload.write(le32Bytes(reqId));
            payload.write(strBytes(url == null ? "" : url));
            payload.write(strBytes(method == null ? "GET" : method));
        } catch (IOException e) {
            pendingSchemes.remove(reqId);
            return null;
        }
        sendEvent(0x0241, payload.toByteArray());

        try {
            return future.get(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable t) {
            pendingSchemes.remove(reqId);
            Log.w(TAG, "scheme resource timeout: " + url);
            return null;
        }
    }

    private void createBrowser(int w, int h, float dsf, boolean transparent, String url) {
        width = w; height = h;
        HttpBridge.current = this;
        main.post(() -> {
            try {
                browser = new CefriumBrowser(w, h);
                browser.setRenderHandler(new RenderHandler());
                if (transparent) call(browser, "setBackgroundColor", 0x00000000);
                registerQueryHandler();
                if (url != null && !url.isEmpty()) call(browser, "loadUrl", url);
            } catch (Throwable t) {
                Log.e(TAG, "createBrowser failed", t);
            }
        });
    }

    /**
     * 把 Cefrium 的 setQueryHandler 接到协议上。
     *
     * <p>签名在 0.17.0 里是 (QueryOriginPolicy, QueryHandler)，
     * 但枚举常量和回调方法名没有公开文档，所以这里按参数类型动态构造：
     * 枚举取第一个常量，接口用 Proxy 接住，回调方法按名字里有没有
     * "query" / "success" / "failure" 来分派。</p>
     */
    private void registerQueryHandler() {
        try {
            Method setter = null;
            for (Method m : browser.getClass().getMethods()) {
                if (m.getName().equals("setQueryHandler") && m.getParameterCount() == 2) {
                    setter = m;
                    break;
                }
            }
            if (setter == null) {
                Log.w(TAG, "Cefrium 没有 setQueryHandler，JS 桥不可用");
                return;
            }
            Class<?>[] params = setter.getParameterTypes();
            Object[] args = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                if (params[i].isEnum()) {
                    Object[] constants = params[i].getEnumConstants();
                    args[i] = constants != null && constants.length > 0 ? constants[0] : null;
                } else if (params[i].isInterface()) {
                    queryHandlerProxy = java.lang.reflect.Proxy.newProxyInstance(
                            params[i].getClassLoader(), new Class<?>[]{params[i]},
                            (proxy, method, a) -> {
                                if (method.getName().equals("toString")) return "MCEFQueryHandler";
                                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                                if (method.getName().equals("equals")) return proxy == a[0];
                                onEngineQuery(method.getName(), a);
                                return null;
                            });
                    args[i] = queryHandlerProxy;
                }
            }
            setter.invoke(browser, args);
            Log.i(TAG, "query handler wired via " + setter);
        } catch (Throwable t) {
            Log.w(TAG, "query handler 挂载失败", t);
        }
    }

    /** 引擎侧回调：找到 request 字符串和 callback 对象，转成 EV_QUERY 发给 MC。 */
    private void onEngineQuery(String methodName, Object[] a) {
        String request = null;
        Object callback = null;
        for (Object o : a) {
            if (o instanceof String && request == null) request = (String) o;
            else if (o != null && !(o instanceof CharSequence) && callback == null) callback = o;
        }
        if (request == null) {
            Log.w(TAG, "引擎查询里没有 String 参数，跳过: " + methodName);
            return;
        }
        long qid = queryIds.getAndIncrement();
        if (callback != null) pendingQueries.put(qid, callback);

        java.io.ByteArrayOutputStream payload = new java.io.ByteArrayOutputStream();
        try {
            payload.write(le64Bytes(qid));
            payload.write(1);                  // persistent
            payload.write(strBytes(""));        // origin
            payload.write(strBytes(request));
        } catch (IOException e) {
            return;
        }
        sendEvent(0x0240, payload.toByteArray());
    }

    private void deliverQueryResult(Object callback, boolean ok, int code, String response) {
        try {
            for (Method m : callback.getClass().getMethods()) {
                String n = m.getName().toLowerCase();
                if (ok && n.contains("success") && m.getParameterCount() == 1) {
                    m.invoke(callback, response);
                    return;
                }
                if (!ok && n.contains("failure")) {
                    if (m.getParameterCount() == 2) m.invoke(callback, code, response);
                    else if (m.getParameterCount() == 1) m.invoke(callback, response);
                    return;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "query callback 调用失败", t);
        }
    }

    private static byte[] le32Bytes(int v) {
        return new byte[]{(byte) v, (byte) (v >>> 8), (byte) (v >>> 16), (byte) (v >>> 24)};
    }

    private static byte[] le64Bytes(long v) {
        byte[] a = le32Bytes((int) (v & 0xFFFFFFFFL));
        byte[] b = le32Bytes((int) (v >>> 32));
        return new byte[]{a[0], a[1], a[2], a[3], b[0], b[1], b[2], b[3]};
    }

    private static byte[] strBytes(String s) {
        byte[] b = s == null ? new byte[0] : s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] len = le32Bytes(b.length);
        byte[] outb = new byte[4 + b.length];
        System.arraycopy(len, 0, outb, 0, 4);
        System.arraycopy(b, 0, outb, 4, b.length);
        return outb;
    }

    private void sendEvent(int type, byte[] payload) {
        if (closed) return;
        try {
            synchronized (writeLock) {
                le32(payload.length);
                le16(type);
                le16(0);
                le32(0);
                out.write(payload);
                out.flush();
            }
        } catch (IOException e) {
            close();
        }
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

    /* 协议是小端；DataOutputStream 的 writeInt 是大端，所以全部自己写 */
    private void le16(int v) throws IOException { out.write(v & 0xFF); out.write((v >>> 8) & 0xFF); }
    private void le32(int v) throws IOException {
        out.write(v & 0xFF); out.write((v >>> 8) & 0xFF);
        out.write((v >>> 16) & 0xFF); out.write((v >>> 24) & 0xFF);
    }
    private void le64(long v) throws IOException { le32((int) (v & 0xFFFFFFFFL)); le32((int) (v >>> 32)); }

    private void writeFrame(int frameSeq, int w, int h, byte[] rgba) {
        if (closed) return;
        try {
            synchronized (writeLock) {
                le32(16 + rgba.length);           // payload: seq,w,h,stride,data
                le16(EV_PAINT);
                le16(0);
                le32(frameSeq);
                le32(frameSeq);
                le32(w);
                le32(h);
                le32(w * 4);
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
        byte[] bytes(int n) {
            byte[] out = Arrays.copyOfRange(b, pos, Math.min(b.length, pos + n));
            pos += n;
            return out;
        }

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
