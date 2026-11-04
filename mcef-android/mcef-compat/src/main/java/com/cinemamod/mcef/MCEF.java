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

        if (!McefNative.isLoaded()) {
            fail("libmcef.so 未加载：" + McefNative.loadError()
                    + "（检查插件 APK 是否安装、FCL 是否把它加进 LD_LIBRARY_PATH）");
            return false;
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

    public static MCEFApp getApp() { assertInitialized(); return app; }

    public static MCEFClient getClient() { assertInitialized(); return client; }

    public static MCEFBrowser createBrowser(String url, boolean transparent) {
        return createBrowser(url, transparent, 1280, 720);
    }

    public static MCEFBrowser createBrowser(String url, boolean transparent, int width, int height) {
        assertInitialized();
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

    private static void assertInitialized() {
        if (!initialized) throw new IllegalStateException("MCEF 未初始化：" + initError);
    }
}
