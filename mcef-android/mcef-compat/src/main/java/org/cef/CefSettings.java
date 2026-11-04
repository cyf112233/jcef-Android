package org.cef;

/** 与桌面 JCEF 同名同义；Android 侧真正生效的只有这几项。 */
public class CefSettings {
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
}
