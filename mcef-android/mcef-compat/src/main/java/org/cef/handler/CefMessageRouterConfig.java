package org.cef.handler;

public class CefMessageRouterConfig {
    public String jsQueryFunction;
    public String jsCancelFunction;

    public CefMessageRouterConfig() { this("cefQuery", "cefQueryCancel"); }

    public CefMessageRouterConfig(String jsQueryFunction, String jsCancelFunction) {
        this.jsQueryFunction = jsQueryFunction;
        this.jsCancelFunction = jsCancelFunction;
    }
}
