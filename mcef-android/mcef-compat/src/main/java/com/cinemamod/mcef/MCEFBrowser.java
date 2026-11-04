package com.cinemamod.mcef;

import com.cinemamod.mcef.listeners.MCEFCursorChangeListener;
import org.cef.browser.CefBrowserOsr;
import org.cef.event.CefKeyEvent;
import org.cef.event.CefMouseEvent;
import org.cef.event.CefMouseWheelEvent;
import org.cef.misc.CefCursorType;

import java.nio.ByteBuffer;

/**
 * 带纹理的 OSR 浏览器。
 *
 * <p>线程约定：{@link #onPaint} 在 native rx 线程，只做 memcpy；
 * {@link #getTextureID()} 在渲染线程，做 GL 上传。</p>
 */
public class MCEFBrowser extends CefBrowserOsr {

    private final MCEFRenderer renderer;
    private MCEFCursorChangeListener cursorChangeListener;
    private volatile int lastWidth;
    private volatile int lastHeight;
    private int btnMask;
    private volatile int mouseX;
    private volatile int mouseY;
    private volatile CefCursorType cursor = CefCursorType.POINTER;
    private boolean browserControls = true;

    public MCEFBrowser(MCEFClient client, String url, boolean transparent) {
        this(client, url, transparent, 1280, 720);
    }

    public MCEFBrowser(MCEFClient client, String url, boolean transparent, int width, int height) {
        super(client.getHandle(), url, true, transparent);
        // 注意：这里绝对不能碰 GL。WebDisplays 建浏览器时不一定在渲染线程上，
        // 没有 current context 时 glGenTextures 在 MobileGlues 上会直接段错误。
        // 纹理在 getTextureID() 里懒创建。
        this.renderer = new MCEFRenderer(transparent);
        this.lastWidth = width;
        this.lastHeight = height;
        resize(width, height);
        createImmediately();
        this.cursorChangeListener = type -> { };
    }

    /** 渲染线程调用，由使用方在画面前取纹理。 */
    public int getTextureID() {
        return renderer.getTextureID();
    }

    public MCEFRenderer getRenderer() { return renderer; }

    @Override
    public void onPaint(int seq, int width, int height, ByteBuffer rgba) {
        renderer.upload(rgba, width, height);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        this.lastWidth = width;
        this.lastHeight = height;
    }

    public int getLastWidth() { return lastWidth; }
    public int getLastHeight() { return lastHeight; }

    public void setCursorChangeListener(MCEFCursorChangeListener listener) {
        if (listener != null) this.cursorChangeListener = listener;
    }

    public MCEFCursorChangeListener getCursorChangeListener() {
        return cursorChangeListener;
    }

    public boolean usingBrowserControls() {
        return browserControls;
    }

    public MCEFBrowser useBrowserControls(boolean value) {
        this.browserControls = value;
        return this;
    }

    @Override
    public void onCursorChange(int cursorType) {
        super.onCursorChange(cursorType);
        cursorChangeListener.onCursorChange(cursorType);
    }

    /* ---------- 输入：GLFW 风格 ---------- */

    private static int modsToCef(int glfwMods) {
        int m = CefMouseEvent.EVENTFLAG_NONE;
        if ((glfwMods & 0x0001) != 0) m |= CefMouseEvent.EVENTFLAG_SHIFT_DOWN;
        if ((glfwMods & 0x0002) != 0) m |= CefMouseEvent.EVENTFLAG_CONTROL_DOWN;
        if ((glfwMods & 0x0004) != 0) m |= CefMouseEvent.EVENTFLAG_ALT_DOWN;
        return m;
    }

    /** @param mouseX / mouseY 为屏幕坐标，内部换算到浏览器视口 */
    public void sendMouseMove(double mouseX, double mouseY) {
        sendMouseMove(mouseX, mouseY, 0, false);
    }

    public void sendMouseMove(double mouseX, double mouseY, int glfwMods, boolean leaving) {
        this.mouseX = (int) mouseX;
        this.mouseY = (int) mouseY;
        CefMouseEvent e = new CefMouseEvent(this.mouseX, this.mouseY, modsToCef(glfwMods) | btnMask);
        super.sendMouseMove(e, leaving);
    }

    /* ---------- 对外签名与上游 MCEFBrowser 对齐（invokevirtual 描述符必须一致） ---------- */

    public void sendMouseMove(int x, int y) {
        sendMouseMove((double) x, (double) y, 0, false);
    }

    public void sendMousePress(int x, int y, int glfwButton) {
        press(x, y, glfwButton, true);
    }

    public void sendMouseRelease(int x, int y, int glfwButton) {
        press(x, y, glfwButton, false);
    }

    /** 上游是 (int, int, double, int)：x, y, delta, modifiers。 */
    public void sendMouseWheel(int x, int y, double delta, int glfwMods) {
        sendMouseWheel((double) x, (double) y, glfwMods, 0, delta);
    }

    public void close() {
        close(true);
    }

    private void press(double x, double y, int glfwButton, boolean down) {
        this.mouseX = (int) x;
        this.mouseY = (int) y;
        int cefButton = glfwButton == 1 ? 2 : glfwButton;   // GLFW right=1 → CEF right=2
        int flag = cefButton == 0 ? CefMouseEvent.EVENTFLAG_LEFT_MOUSE_BUTTON
                : cefButton == 1 ? CefMouseEvent.EVENTFLAG_MIDDLE_MOUSE_BUTTON
                : CefMouseEvent.EVENTFLAG_RIGHT_MOUSE_BUTTON;
        btnMask = down ? (btnMask | flag) : (btnMask & ~flag);
        CefMouseEvent e = new CefMouseEvent(mouseX, mouseY, modsToCef(0) | btnMask);
        super.sendMousePress(e, cefButton, down);
    }

    /* ---------- WebDisplays 用到的其余入口 ---------- */

    /** 键盘：GLFW 键码 + scancode + mods。 */
    public void sendKeyPress(int glfwKey, long scanCode, int glfwMods) {
        CefKeyEvent e = new CefKeyEvent();
        e.type = CefKeyEvent.KEYEVENT_KEYDOWN;
        e.modifiers = modsToCef(glfwMods);
        e.windows_key_code = glfwToVk(glfwKey);
        e.native_key_code = (int) (scanCode != 0 ? scanCode : glfwKey);
        super.sendKeyEvent(e);
    }

    public void sendKeyRelease(int glfwKey, long scanCode, int glfwMods) {
        CefKeyEvent e = new CefKeyEvent();
        e.type = CefKeyEvent.KEYEVENT_KEYUP;
        e.modifiers = modsToCef(glfwMods);
        e.windows_key_code = glfwToVk(glfwKey);
        e.native_key_code = (int) (scanCode != 0 ? scanCode : glfwKey);
        super.sendKeyEvent(e);
    }

    public void sendKeyTyped(char ch, int glfwMods) {
        CefKeyEvent e = new CefKeyEvent();
        e.type = CefKeyEvent.KEYEVENT_CHAR;
        e.modifiers = modsToCef(glfwMods);
        e.character = ch;
        e.unmodified_character = ch;
        super.sendKeyEvent(e);
    }

    /** 光标形状由页面决定，MC 侧只做展示，这里记录最新值。 */
    public void setCursor(CefCursorType type) {
        this.cursor = type;
    }

    public CefCursorType getCursor() { return cursor; }

    public boolean isPageLoading() {
        return isLoading();
    }

    public void runJS(String code, String scriptUrl) {
        executeJavaScript(code, scriptUrl == null ? "" : scriptUrl, 0);
    }

    public void sendMouseWheel(double mouseX, double mouseY, int glfwMods, double dx, double dy) {
        CefMouseWheelEvent e = new CefMouseWheelEvent((int) mouseX, (int) mouseY,
                modsToCef(glfwMods), (int) (dx * 120), (int) (dy * 120));
        super.sendMouseWheel(e);
    }

    /**
     * GLFW 键码 → CEF。GLFW 与 Windows VK 只覆盖常用键，
     * 其余先按 scancode 0 透传，再由 host 侧映射 Android KeyEvent。
     */
    public void sendKey(int glfwKey, int glfwAction, int glfwMods, char ch) {
        CefKeyEvent e = new CefKeyEvent();
        e.modifiers = modsToCef(glfwMods);
        e.native_key_code = glfwKey;
        e.windows_key_code = glfwToVk(glfwKey);
        if (glfwAction == 0) {
            e.type = CefKeyEvent.KEYEVENT_KEYDOWN;
            super.sendKeyEvent(e);
        } else if (glfwAction == 1) {
            e.type = CefKeyEvent.KEYEVENT_KEYUP;
            super.sendKeyEvent(e);
        } else if (glfwAction == 2 && ch != 0) {
            e.type = CefKeyEvent.KEYEVENT_CHAR;
            e.character = ch;
            e.unmodified_character = ch;
            super.sendKeyEvent(e);
        }
    }

    private static int glfwToVk(int key) {
        // GLFW 的字母/数字与 VK 码一致；功能键单独映射
        if (key >= 32 && key <= 96) return key;
        switch (key) {
            case 256: return 0x1B;   // ESCAPE
            case 257: return 0x0D;   // ENTER
            case 258: return 0x09;   // TAB
            case 259: return 0x08;   // BACKSPACE
            case 261: return 0x2E;   // DELETE
            case 262: return 0x27;   // RIGHT
            case 263: return 0x25;   // LEFT
            case 264: return 0x28;   // DOWN
            case 265: return 0x26;   // UP
            case 266: return 0x21;   // PAGE_UP
            case 267: return 0x22;   // PAGE_DOWN
            case 268: return 0x24;   // HOME
            case 269: return 0x23;   // END
            default: break;
        }
        if (key >= 290 && key <= 301) return 0x70 + (key - 290);  // F1..F12
        return key;
    }

    @Override
    public void close(boolean force) {
        // 同理：只有确定在渲染线程上才删纹理，否则交给调用方
        try {
            renderer.destroy();
        } catch (Throwable ignored) {
            // 没有 context 时删不掉也没关系，进程退出会回收
        }
        super.close(force);
    }
}
