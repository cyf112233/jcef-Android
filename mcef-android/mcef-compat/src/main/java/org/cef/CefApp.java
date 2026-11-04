package org.cef;

import java.io.File;

/**
 * 进程级入口。Android 上引擎的初始化在插件 APK 里完成（Cefrium 的多进程模型），
 * 这里只负责建立到 host 的连接状态，所以 startup() 是幂等的。
 */
public class CefApp {

    private static CefApp instance;
    private static boolean initialized;
    private static String version = "Cefrium/Android (unknown)";

    private final CefSettings settings;

    private CefApp(CefSettings settings) { this.settings = settings; }

    public static synchronized boolean startup(String[] args) {
        return true;
    }

    public static synchronized CefApp getInstance(String[] args, CefSettings settings) {
        if (instance == null) {
            instance = new CefApp(settings == null ? new CefSettings() : settings);
            initialized = true;
        }
        return instance;
    }

    public static synchronized CefApp getInstance() {
        return getInstance(null, null);
    }

    public static synchronized boolean isInitialized() { return initialized; }

    public static String getVersion() { return version; }

    public static void setVersion(String v) { if (v != null) version = v; }

    public CefSettings getSettings() { return settings; }

    /**
     * 自定义协议注册。真正的拦截在 host 侧（Cefrium 的请求拦截），
     * 这里只保存工厂，查询时由 CefBrowserOsr 回调。
     */
    private static final java.util.Map<String, org.cef.callback.CefSchemeHandlerFactory> SCHEMES =
            new java.util.concurrent.ConcurrentHashMap<>();

    public boolean registerSchemeHandlerFactory(String schemeName, String domainName,
                                                org.cef.callback.CefSchemeHandlerFactory factory) {
        if (schemeName == null || factory == null) return false;
        SCHEMES.put(schemeName.toLowerCase(java.util.Locale.US), factory);
        return true;
    }

    public boolean clearSchemeHandlerFactories() {
        SCHEMES.clear();
        return true;
    }

    public static org.cef.callback.CefSchemeHandlerFactory getSchemeHandlerFactory(String scheme) {
        return scheme == null ? null
                : SCHEMES.get(scheme.toLowerCase(java.util.Locale.US));
    }

    public void onBeforeCommandLineProcessing(String processType, String commandLine) {
        // host 侧在 Cefrium 初始化前处理命令行；这里保留 API 形状
    }

    public File getCachePath() {
        return settings.cache_path == null ? null : new File(settings.cache_path);
    }

    public synchronized void dispose() {
        initialized = false;
        instance = null;
    }
}
