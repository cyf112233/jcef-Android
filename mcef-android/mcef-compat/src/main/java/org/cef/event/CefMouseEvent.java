package org.cef.event;

public class CefMouseEvent {
    public static final int EVENTFLAG_NONE = 0;
    public static final int EVENTFLAG_SHIFT_DOWN = 1 << 1;
    public static final int EVENTFLAG_CONTROL_DOWN = 1 << 2;
    public static final int EVENTFLAG_ALT_DOWN = 1 << 3;
    public static final int EVENTFLAG_LEFT_MOUSE_BUTTON = 1 << 4;
    public static final int EVENTFLAG_MIDDLE_MOUSE_BUTTON = 1 << 5;
    public static final int EVENTFLAG_RIGHT_MOUSE_BUTTON = 1 << 6;

    public int x;
    public int y;
    public int modifiers;

    public CefMouseEvent() { this(0, 0, 0); }

    public CefMouseEvent(int x, int y, int modifiers) {
        this.x = x; this.y = y; this.modifiers = modifiers;
    }
}
