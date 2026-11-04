package org.cef.callback;

import org.cef.browser.CefBrowser;
import org.cef.handler.CefResourceHandler;
import org.cef.network.CefRequest;

/** 自定义协议工厂，对应 cef_register_scheme_handler_factory。 */
public interface CefSchemeHandlerFactory {
    CefResourceHandler create(CefBrowser browser, String frameName, String schemeName, CefRequest request);
}
