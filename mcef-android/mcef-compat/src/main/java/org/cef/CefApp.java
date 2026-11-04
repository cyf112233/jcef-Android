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

    public static synchronized CefApp getInstance(String[] args) {
        return getInstance(args, null);
    }

    /** 上游的实例方法；Android 侧初始化在插件里，这里只保证幂等。 */
    public synchronized void initialize() {
        // no-op
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

    /** 回环 HTTP 桥的端口，host 侧 HttpBridge 监听同一个值。 */
    public static final int LOOPBACK_PORT = 39091;

    /**
     * 把注册过的自定义协议改写成回环 HTTP。
     *
     * <p>Cefrium 的 onRequestIntercepted 只能观察不能接管，所以引擎层面实现不了
     * scheme handler。这里把 webdisplays://a/b 换成
     * http://127.0.0.1:39091/mcef/&lt;base64url(原始 URL)&gt;，
     * host 收到 HTTP 请求后再回问 MC 要 CefResourceHandler 的内容。</p>
     */
    public static String rewriteUrl(String url) {
        if (url == null) return null;
        int sep = url.indexOf("://");
        if (sep <= 0) return url;
        String scheme = url.substring(0, sep).toLowerCase(java.util.Locale.US);
        if (!SCHEMES.containsKey(scheme)) return url;
        String b64 = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "http://127.0.0.1:" + LOOPBACK_PORT + "/mcef/" + b64;
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
