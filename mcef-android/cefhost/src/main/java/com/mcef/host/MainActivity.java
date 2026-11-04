package com.mcef.host;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

/**
 * 只是个入口：FCL 的插件扫描只认有 LAUNCHER intent 的应用。
 * 打开它 = 拉起常驻服务，之后 MC 进程能连上。
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView tv = new TextView(this);
        tv.setText("MCEF Host\n\nChromium 引擎常驻服务已启动。\n"
                + "FCL 会把这个 APK 的 lib 目录加进 LD_LIBRARY_PATH。\n"
                + "现在可以回启动器进游戏了。");
        tv.setPadding(48, 96, 48, 48);
        tv.setTextSize(16);
        setContentView(tv);

        Intent svc = new Intent(this, CefHostService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
        else startService(svc);
    }
}
