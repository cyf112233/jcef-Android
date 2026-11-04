package com.mcef.host;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** 常驻进程，负责监听 127.0.0.1:39090。 */
public class CefHostService extends Service {

    private static final String CHANNEL = "mcef-host";
    private IpcServer server;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "MCEF Host",
                NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("MCEF Host")
                .setContentText("Chromium 引擎运行中")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build();
        startForeground(1, n);

        server = new IpcServer(this);
        server.start();
        HttpBridge.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (server != null) server.stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
