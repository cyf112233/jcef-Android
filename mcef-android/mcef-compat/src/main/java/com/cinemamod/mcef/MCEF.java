package com.cinemamod.mcef;

import com.cinemamod.mcef.listeners.MCEFInitListener;
import com.cinemamod.mcef.nativebridge.McefNative;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;

import java.util.ArrayList;
import java.util.List;

/** 门面，和桌面版 MCEF 保持同名入口。 */
public class MCEF {

    private static MCEFSettings settings;
    private static MCEFApp app;
    private static MCEFClient client;
    private static boolean initialized;
    private static String initError;

    private static final List<MCEFInitListener> awaitingInit = new ArrayList<>();

    public static void scheduleForInit(MCEFInitListener task) {
        if (task == null) return;
        synchronized (awaitingInit) {
            if (!initialized && initError == null) { awaitingInit.add(task); return; }
        }
        if (initialized) task.onInit(); else task.onError(initError);
    }

    public static MCEFSettings getSettings() {
        if (settings == null) settings = MCEFSettings.load();
        return settings;
    }

    public static synchronized boolean initialize() {
        if (initialized) return true;

        // libmcef.so 没加载也照常初始化：让消费方的 isInitialized() 为真、
        // 能注册 handler，浏览器退化成"空白但不崩"，比直接抛异常好排查。
        if (!McefNative.isLoaded()) {
            initError = "libmcef.so 未加载：" + McefNative.loadError()
                    + "（检查插件 APK 是否安装、FCL 是否把它加进 LD_LIBRARY_PATH）";
            System.err.println("[MCEF-Android] " + initError);
        }

        CefSettings cefSettings = new CefSettings();
        cefSettings.windowless_rendering_enabled = true;
        CefApp cefApp = CefApp.getInstance(null, cefSettings);
        CefClient cefClient = new CefClient();

        app = new MCEFApp(cefApp);
        client = new MCEFClient(cefClient);
        initialized = true;

        List<MCEFInitListener> copy;
        synchronized (awaitingInit) { copy = new ArrayList<>(awaitingInit); awaitingInit.clear(); }
        for (MCEFInitListener l : copy) l.onInit();
        return true;
    }

    private static void fail(String message) {
        initError = message;
        System.err.println("[MCEF-Android] " + message);
        List<MCEFInitListener> copy;
        synchronized (awaitingInit) { copy = new ArrayList<>(awaitingInit); awaitingInit.clear(); }
        for (MCEFInitListener l : copy) l.onError(message);
    }

    public static MCEFApp getApp() {
        ensureInitialized();
        return app;
    }

    public static MCEFClient getClient() {
        ensureInitialized();
        return client;
    }

    /**
     * 我们是 lowcode 纯库，没有 @Mod 入口类，所以没有人替我们调 initialize()。
     * 改成第一次用到就自举，语义和原版一致（原版是在 mod 构造器里初始化）。
     */
    private static void ensureInitialized() {
        if (initialized) return;
        synchronized (MCEF.class) {
            if (initialized) return;
            initialize();
        }
        if (!initialized) {
            throw new IllegalStateException("MCEF 初始化失败：" + initError);
        }
    }

    public static MCEFBrowser createBrowser(String url, boolean transparent) {
        return createBrowser(url, transparent, 1280, 720);
    }

    public static MCEFBrowser createBrowser(String url, boolean transparent, int width, int height) {
        ensureInitialized();
        return new MCEFBrowser(client, url, transparent, width, height);
    }

    public static boolean isInitialized() { return initialized; }
    public static String getInitError() { return initError; }

    public static String getVersion() { return CefApp.getVersion(); }

    public static synchronized void shutdown() {
        if (client != null) client.getHandle().dispose();
        if (app != null) app.getHandle().dispose();
        client = null;
        app = null;
        initialized = false;
    }

    /** 类加载即初始化：WebDisplays 在 ClientProxy 里先问 isInitialized() 再注册 scheme/display。 */
    static {
        try {
            initialize();
        } catch (Throwable t) {
            initError = String.valueOf(t);
            System.err.println("[MCEF-Android] 类初始化阶段自举失败: " + t);
        }
    }
}
