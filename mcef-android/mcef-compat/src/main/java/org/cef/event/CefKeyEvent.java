package org.cef.event;

public class CefKeyEvent {
    public static final int KEYEVENT_RAWKEYDOWN = 0;
    public static final int KEYEVENT_KEYDOWN = 1;
    public static final int KEYEVENT_KEYUP = 2;
    public static final int KEYEVENT_CHAR = 3;

    public int type;
    public int modifiers;
    public int windows_key_code;
    public int native_key_code;
    public char character;
    public char unmodified_character;
    public boolean is_system_key;
    public int focus_on_editable_field;

    public int getKeyCode() { return windows_key_code; }
    public void setKeyCode(int code) { windows_key_code = code; }
    public int getModifiers() { return modifiers; }
    public void setModifiers(int m) { modifiers = m; }
    public int getType() { return type; }
}
