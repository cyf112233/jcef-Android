package com.cinemamod.mcef;

import org.cef.CefApp;

/** CefApp 的壳，保留桌面版的方法名。 */
public class MCEFApp {
    private final CefApp handle;

    public MCEFApp(CefApp handle) { this.handle = handle; }

    public CefApp getHandle() { return handle; }
}
