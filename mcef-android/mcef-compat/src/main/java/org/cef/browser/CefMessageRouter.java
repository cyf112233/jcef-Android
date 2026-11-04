package org.cef.browser;

import org.cef.handler.CefMessageRouterConfig;
import org.cef.handler.CefMessageRouterHandler;

/**
 * JS ↔ Java 查询桥。WebDisplays 的整套交互（取元素坐标、执行脚本、订阅）
 * 都挂在它上面。
 */
public class CefMessageRouter {

    private final CefMessageRouterConfig config;
    private final CefMessageRouterHandler handler;

    private CefMessageRouter(CefMessageRouterConfig config, CefMessageRouterHandler handler) {
        this.config = config;
        this.handler = handler;
    }

    public static CefMessageRouter create(CefMessageRouterHandler handler) {
        return new CefMessageRouter(new CefMessageRouterConfig(), handler);
    }

    public static CefMessageRouter create(CefMessageRouterConfig config, CefMessageRouterHandler handler) {
        return new CefMessageRouter(config == null ? new CefMessageRouterConfig() : config, handler);
    }

    public CefMessageRouterConfig getConfig() { return config; }

    public CefMessageRouterHandler getHandler() { return handler; }
}
