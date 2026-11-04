package com.mcef.host;

import android.content.Context;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 127.0.0.1:39090 的服务器，一个连接 = 一个浏览器。 */
public class IpcServer {

    static final String TAG = "MCEF-Host";
    static final int PORT = 39090;
    static final int VERSION = 1;

    private final Context context;
    private final Set<BrowserSession> sessions = ConcurrentHashMap.newKeySet();
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    IpcServer(Context context) { this.context = context.getApplicationContext(); }

    void start() {
        running = true;
        acceptThread = new Thread(this::acceptLoop, "mcef-accept");
        acceptThread.start();
    }

    void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) { }
        for (BrowserSession s : sessions) s.close();
        sessions.clear();
    }

    private void acceptLoop() {
        try {
            serverSocket = new ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1"));
            serverSocket.setReuseAddress(true);
            Log.i(TAG, "listening on 127.0.0.1:" + PORT);
        } catch (IOException e) {
            Log.e(TAG, "bind failed", e);
            return;
        }

        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                // MC 进程和插件不同 uid；只接受本机回环，不做别的鉴权
                Thread t = new Thread(() -> serve(socket), "mcef-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (running) Log.w(TAG, "accept failed", e);
            }
        }
    }

    private void serve(Socket socket) {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            byte[] magic = new byte[4];
            in.readFully(magic);
            short ver = in.readShort();
            in.readShort();
            if (magic[0] != 'M' || ver != VERSION) {
                out.write("MCEF".getBytes());
                out.writeShort(VERSION);
                out.writeShort(2);
                out.flush();
                socket.close();
                return;
            }
            out.write("MCEF".getBytes());
            out.writeShort(VERSION);
            out.writeShort(0);
            out.flush();

            BrowserSession session = new BrowserSession(context, in, out, socket);
            sessions.add(session);
            session.run();
        } catch (IOException e) {
            Log.d(TAG, "connection closed: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }
}
