# mcef-android

在 Android 的 Minecraft Java 版启动器（FCL / Pojav / Zalith）里运行**真正的 MCEF**：
引擎是 Cefrium 打包的真 libcef（Chromium 156），不是系统 WebView。

## 为什么是这个形状

Minecraft 跑在纯 OpenJDK JVM 里（不是 ART），加载不到 `android.*`；
而 libcef 的 Android 移植初始化必须拿到 `Context`，只能 attach ART。
一个进程里 libcef 只 attach 一个 VM，所以 libjcef.so **不可能**直接跑在 MC 的 JVM 里。

因此分三层：

```
┌─ MC 进程（OpenJDK JVM）────────────────────────────┐
│  com.cinemamod.mcef.*  /  org.cef.*   ← 本仓库     │
│        原版 MCEF 的 Java 代码路径原样保留           │
│                 └─ McefNative (JNI)                │
│                        libmcef.so                  │
└────────────────────────┬───────────────────────────┘
                         │ TCP 127.0.0.1:39090
┌────────────────────────▼───────────────────────────┐
│  插件 APK 进程（ART + Cefrium + 真 libcef）        │
│  CefHostService → CefriumBrowser(OSR)               │
└────────────────────────────────────────────────────┘
```

插件 APK 是独立应用，所以 Cefrium SDK 需要的 manifest 多进程 service、R 资源表、
assets 全部由它自己提供，不需要改 FCL。FCL 那一侧只用 `FCLNativePlugin`
把插件的 `nativeLibraryDir` 拼进 `LD_LIBRARY_PATH`（`FCLauncher.java:165,230`），
让 MC 进程里的 `System.loadLibrary("mcef")` 能找到 so。

## 模块

| 目录 | 语言 | 产物 | 跑在哪 |
|---|---|---|---|
| `mcef-compat` | Java 17 | `mcef-android.jar`（mod） | MC 的 OpenJDK JVM |
| `mcef-native` | C11 + NDK | `libmcef.so` | MC 进程 |
| `cefhost` | Java | 插件 APK | 自己的 ART 进程 |

## 构建

```bash
# 1. 原生库（NDK r27+）
cd mcef-native && cmake -B build -DANDROID_ABI=arm64-v8a \
  -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -G Ninja && ninja -C build

# 2. 插件 APK（需要 JDK 25 / AGP 9.4 / compileSdk 37，Cefrium 的硬要求）
./gradlew :cefhost:assembleRelease

# 3. mod jar
./gradlew :mcef-compat:jar
```

安装：插件 APK 正常安装（FCL 插件页会自动识别 `FCLNativePlugin`），
mod jar 丢进 `mods/`。首次启动时 mod 会尝试拉起插件的 `CefHostService`。

## 功能矩阵

| 能力 | 状态 | 走哪条 |
|---|---|---|
| 页面加载 / 导航 / 前进后退 | 实现 | CREATE/LOAD_URL/GO_BACK… |
| OSR 画面 → MC 纹理 | 实现 | PAINT 事件 + getTextureID() 上传 |
| 鼠标 / 滚轮 / 键盘 | 实现 | MOUSE_*/KEY_* |
| IME 输入法 | 协议就位 | IME_* 三帧 |
| JS 执行 + 回调 | 实现 | EVAL_JS ↔ JS_RESULT |
| 加载状态 / 地址 / 标题 / 状态栏 | 实现 | LOAD_STATE / ADDRESS… |
| 光标形状 | 实现 | CURSOR_CHANGE |
| 弹窗（select、日期选择器） | 实现 | POPUP_* |
| Cookie / 存储 | 实现 | COOKIE_* |
| 缩放 / 背景色 / 焦点 | 实现 | ZOOM / SET_BACKGROUND / SET_FOCUS |
| 渲染进程崩溃恢复 | 部分 | RENDER_TERMINATED，无自动重启 |
| 下载 / 右键菜单 / 文件选择 | 协议预留 | 见 PROTOCOL.md 0x022B、0x022C |
| DevTools | 不支持 | Cefrium 无远程调试端口的对外开关 |
| 视频硬解画面 | 不支持 | OSR + GPU overlay，CEF 通病（RainClay 已确认） |

## 与 WebDisplays

`org.cef.*` 与 `com.cinemamod.mcef.*` 按 CinemaMod 分支的签名实现，
WebDisplays 2.0.2 的调用面（22 类 / 42 成员）全部覆盖；多出来的成员不是空壳，
要么转发到协议里的对应命令，要么在 `UnsupportedOperationException` 里写明原因。

## 许可

本仓库 LGPL-2.1（与 MCEF 一致）。Cefrium 为 LGPL-3.0-or-later，随插件 APK 分发时
需遵守其条款（可替换库、附带许可证与源码获取方式）。

## 当前进度（2026-11-03）

已验证：

- `mcef-native`：`gcc -Wall -Wextra` 编译通过（用 JDK 的 jni.h 验证，无 NDK 环境）
- `mcef-compat`：`javac` 全量编译通过（LWJGL 用桩类替代）
- `cefhost`：**未编译**，本机没有 Android SDK / AGP 9 / Cefrium AAR

尚未打通 / 已知缺口：

| 项 | 状态 | 说明 |
|---|---|---|
| Cefrium 输入 API 方法名 | 待对齐 | host 侧用反射调 `sendMouseEvent` / `sendKeyEvent`，0.17.0 javadoc 只列了 `MOUSE_BUTTON_*` / `KEY_*` 常量，没列出方法签名；名字对不上会打 warning 并继续跑 |
| JS 返回值回传 | 待补 | `evaluateJavaScript` 在 Cefrium 里似乎只有 void 版本，`EV_JS_RESULT` 目前不会发出 |
| resize | 降级实现 | Cefrium 无 resize，host 重建浏览器并重载 URL，页面状态会丢 |
| 插件自动拉起 | 未实现 | 现在要手动打开一次插件 App；后续可在 native 里用 ART 起 `startForegroundService` |
| 多浏览器并发 | 协议支持 | 一条连接一个浏览器，`MCEF.createBrowser` 多次调用会开多条连接 |
| 视频 / DevTools | 不支持 | 见上表 |

## 下一步验证顺序

1. 在真机上装插件 APK，打开一次，确认 `adb logcat | grep MCEF-Host` 出现 `listening on 127.0.0.1:39090`
2. 用 `nc 127.0.0.1 39090` 手工发握手，确认返回 `MCEF\x01\x00\x00\x00\x00`
3. 进游戏，模组触发 `MCEF.initialize()`，看 logcat 有没有 `createBrowser failed` —— 这一步会暴露 Cefrium 的实际方法名，再回来把反射换成直接调用

