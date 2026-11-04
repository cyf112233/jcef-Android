package org.cef;

/** 与桌面 JCEF 同名同义；Android 侧真正生效的只有这几项。 */
public class CefSettings {

    public enum LogSeverity {
        LOGSEVERITY_DEFAULT, LOGSEVERITY_VERBOSE, LOGSEVERITY_INFO,
        LOGSEVERITY_WARNING, LOGSEVERITY_ERROR, LOGSEVERITY_FATAL, LOGSEVERITY_DISABLE;

        public static LogSeverity fromInt(int v) {
            LogSeverity[] all = values();
            return v >= 0 && v < all.length ? all[v] : LOGSEVERITY_DEFAULT;
        }
    }

    public String browser_subprocess_path = null;
    public boolean windowless_rendering_enabled = true;
    public boolean external_message_pump = false;
    public boolean multi_threaded_message_loop = true;
    public boolean command_line_args_disabled = false;
    public String cache_path = null;
    public String user_agent = null;
    public String locale = null;
    public int remote_debugging_port = 0;
    public int log_severity = 0;
    public String log_file = null;
    public boolean persist_session_cookies = true;
    public int background_color = 0xFFFFFFFF;

    @Override
    public CefSettings clone() {
        CefSettings s = new CefSettings();
        s.browser_subprocess_path = browser_subprocess_path;
        s.windowless_rendering_enabled = windowless_rendering_enabled;
        s.cache_path = cache_path;
        s.user_agent = user_agent;
        s.locale = locale;
        s.log_file = log_file;
        s.background_color = background_color;
        return s;
    }
}
