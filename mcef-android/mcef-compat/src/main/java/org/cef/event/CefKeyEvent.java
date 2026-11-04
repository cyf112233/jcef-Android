package org.cef.event;

public class CefKeyEvent {
    public static final int KEYEVENT_RAWKEYDOWN = 0;
    public static final int KEYEVENT_KEYDOWN = 1;
    public static final int KEYEVENT_KEYUP = 2;
    public static final int KEYEVENT_CHAR = 3;

    public int type;
    public int modifiers;
    /** Windows VK 码；GLFW 键码由 MCEFBrowser 换算。 */
    public int windows_key_code;
    public int native_key_code;
    public char character;
    public char unmodified_character;
    public boolean is_system_key;
    public int focus_on_editable_field;
}
