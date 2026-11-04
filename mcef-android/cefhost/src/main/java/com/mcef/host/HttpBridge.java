package com.mcef.host;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 自定义协议的回环桥。
 *
 * <p>Cefrium 的 onRequestIntercepted 是 void，只能观察不能接管，
 * 所以没法真的实现 webdisplays:// 这种 scheme handler。折中方案：
 * MC 侧把 webdisplays://a/b 改写成 http://127.0.0.1:39091/mcef/<base64>，
 * 这里收到 HTTP 请求后回问 MC 要真正的资源内容。</p>
 */
public final class HttpBridge {

    static final String TAG = "MCEF-Host";
    static final int PORT = 39091;

    /** 当前活跃的浏览器会话；多屏时会互相覆盖，够用就行。 */
    static volatile BrowserSession current;

    private static volatile boolean running;
    private static ServerSocket server;

    static void start() {
        if (running) return;
        running = true;
        Thread t = new Thread(HttpBridge::acceptLoop, "mcef-http");
        t.setDaemon(true);
        t.start();
    }

    private static void acceptLoop() {
        try {
            server = new ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1"));
            Log.i(TAG, "http bridge on 127.0.0.1:" + PORT);
        } catch (IOException e) {
            Log.e(TAG, "http bridge bind failed", e);
            return;
        }
        while (running) {
            try {
                Socket s = server.accept();
                Thread t = new Thread(() -> serve(s), "mcef-http-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (running) Log.w(TAG, "http accept failed", e);
            }
        }
    }

    private static void serve(Socket socket) {
        try (Socket s = socket) {
            DataInputStream in = new DataInputStream(s.getInputStream());
            String requestLine = readLine(in);
            if (requestLine == null) return;
            // 吃掉请求头
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) { /* ignore */ }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String path = parts[0];
            String method = parts[1];

            String original = null;
            if (path.startsWith("/mcef/")) {
                String b64 = path.substring("/mcef/".length());
                int q = b64.indexOf('?');
                if (q >= 0) b64 = b64.substring(0, q);
                try {
                    original = new String(Base64.getUrlDecoder().decode(b64), StandardCharsets.UTF_8);
                } catch (Throwable t) {
                    Log.w(TAG, "bad bridge url: " + b64, t);
                }
            }

            BrowserSession session = current;
            if (original == null || session == null) {
                writeResponse(s, 503, "text/plain", "no bridge".getBytes(StandardCharsets.UTF_8));
                return;
            }

            BrowserSession.SchemeReply reply = session.fetchSchemeResource(original, method);
            if (reply == null) {
                writeResponse(s, 504, "text/plain", "timeout".getBytes(StandardCharsets.UTF_8));
            } else if (!reply.found) {
                writeResponse(s, reply.status == 0 ? 404 : reply.status, reply.mime,
                        reply.body == null ? new byte[0] : reply.body);
            } else {
                writeResponse(s, reply.status == 0 ? 200 : reply.status, reply.mime, reply.body);
            }
        } catch (IOException e) {
            Log.d(TAG, "http bridge connection: " + e.getMessage());
        }
    }

    private static String readLine(DataInputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) break;
        }
        return c == -1 && sb.length() == 0 ? null : sb.toString();
    }

    private static void writeResponse(Socket s, int status, String mime, byte[] body) throws IOException {
        OutputStream out = new BufferedOutputStream(s.getOutputStream());
        String head = "HTTP/1.1 " + status + " " + reason(status) + "\r\n"
                + "Content-Type: " + (mime == null ? "application/octet-stream" : mime) + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    private static String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 503: return "Service Unavailable";
            case 504: return "Gateway Timeout";
            default: return "Status";
        }
    }

    private HttpBridge() {}
}
