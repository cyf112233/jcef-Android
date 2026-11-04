# MCEF-Android IPC Protocol v1

传输：TCP `127.0.0.1:39090`（SOCK_STREAM）。所有整数**小端**。
每次连接建立一个浏览器会话；一进程多浏览器 = 多连接。

## 握手

```
C → H   4s magic = "MCEF"    u16 version = 1    u16 reserved = 0
H → C   4s magic = "MCEF"    u16 version = 1    u16 status  (0 = OK, 1 = BUSY, 2 = VERSION)
```

## 帧格式

```
u32 length   // 不含 header 的 payload 字节数
u16 type
u16 flags    // bit0: 需要应答（应答用同一 id）
u32 id       // 命令 id；应答与事件复用
payload
```

字符串：`u32 len + UTF-8 bytes`（不含结尾 0）。

## C → H（0x01xx）

| type | 名称 | payload |
|---|---|---|
| 0x0101 | CREATE_BROWSER | u32 w, u32 h, f32 dsf, u8 transparent, str url |
| 0x0102 | DESTROY | — |
| 0x0103 | LOAD_URL | str url |
| 0x0104 | RESIZE | u32 w, u32 h, f32 dsf |
| 0x0110 | GO_BACK | — |
| 0x0111 | GO_FORWARD | — |
| 0x0112 | RELOAD | u8 ignoreCache |
| 0x0113 | STOP | — |
| 0x0120 | MOUSE_MOVE | i32 x, i32 y, u32 mods, u8 leave |
| 0x0121 | MOUSE_DOWN | i32 x, i32 y, u32 mods, u8 button, u16 count |
| 0x0122 | MOUSE_UP | i32 x, i32 y, u32 mods, u8 button, u16 count |
| 0x0123 | MOUSE_WHEEL | i32 x, i32 y, u32 mods, i32 dx, i32 dy |
| 0x0130 | KEY | u8 kind(0 rawdown/1 down/2 up/3 char), i32 key, i32 scan, u32 mods, u16 chr |
| 0x0140 | SET_FOCUS | u8 focused |
| 0x0141 | IME_SET_COMPOSITION | str text, i32 selStart, i32 selEnd, i32 caret |
| 0x0142 | IME_COMMIT | str text |
| 0x0143 | IME_CANCEL | — |
| 0x0150 | EVAL_JS | str code（结果走 0x0226，id 对应） |
| 0x0151 | SET_ZOOM | f64 level |
| 0x0152 | SET_BACKGROUND | u32 argb |
| 0x0153 | SET_FOCUSED_NODE_HELP | u8 enable（是否上报 0x0228） |
| 0x0160 | COOKIE_SET | str url, str name, str value, str domain, str path, u8 secure, u8 httpOnly, i64 expires |
| 0x0161 | COOKIE_GET | str url, str name |
| 0x0162 | COOKIE_FLUSH | — |
| 0x0160 | QUERY_REPLY | i64 queryId, u8 ok, i32 errorCode, str response |
| 0x0161 | SCHEME_RESPONSE | u32 reqId, u8 found, i32 status, str mime, u32 len, bytes body |
| 0x0190 | PING | u32 stamp |

## H → C（0x02xx）

| type | 名称 | payload |
|---|---|---|
| 0x0201 | PAINT | u32 seq, u32 w, u32 h, u32 stride, bytes rgba |
| 0x0202 | POPUP_PAINT | u32 seq, u32 w, u32 h, u32 stride, bytes rgba |
| 0x0203 | POPUP_SHOW | u8 show |
| 0x0204 | POPUP_SIZE | u32 x, u32 y, u32 w, u32 h |
| 0x0210 | CURSOR_CHANGE | i32 cursorType（CEF 枚举） |
| 0x0220 | LOAD_STATE | u8 loading, u8 canBack, u8 canForward |
| 0x0221 | ADDRESS_CHANGE | str url |
| 0x0222 | TITLE_CHANGE | str title |
| 0x0223 | STATUS_MESSAGE | str msg |
| 0x0224 | CONSOLE_MESSAGE | i32 level, str msg, str source, i32 line |
| 0x0225 | BEFORE_POPUP | str url |
| 0x0226 | JS_RESULT | u8 ok, str result |
| 0x0227 | AFTER_CREATED | str url |
| 0x0228 | FOCUSED_NODE | u8 editable, str value, str placeholder, str type |
| 0x0229 | SELECTION_CHANGED | str text |
| 0x022A | RENDER_TERMINATED | i32 status |
| 0x022B | DOWNLOAD_BEGIN | str url, str suggestedName, i64 total |
| 0x022C | CONTEXT_MENU | u32 x, u32 y, str linkUrl, str pageUrl, u8 type |
| 0x0230 | PONG | u32 stamp |
| 0x0231 | ACK | u16 err |
| 0x0240 | QUERY | i64 queryId, u8 persistent, str origin, str request |
| 0x0241 | SCHEME_REQUEST | u32 reqId, str url, str method |

## 像素格式

RGBA8888，premultiplied alpha，stride = w*4。
和 GL_RGBA 直接对齐，不需要桌面 CEF 那个 BGRA 换序。

## 错误与超时

- 连接失败：`McefNative.connect()` 返回 -1，上层回落到"未初始化"而不是崩溃
- 3 秒无 PONG：视为 host 死亡，浏览器标记 terminated，后续调用返回 ERR_CLOSED
- 未识别的 type：丢弃并回 ACK(err=ERR_UNSUPPORTED)，不中断连接

## 为什么不用 Unix socket 传 fd（ashmem）

跨 uid 的 abstract socket 在部分 SELinux 策略下会被拦，
自 0x0201 起用全帧拷贝：1280x720x4 ≈ 3.6MB/帧，
Cefrium 的 `onPaint` 本身也是整帧回调，没有 dirty rect 可省。
后续可在同进程（host 与 UI 同 uid）时升级为 `ASharedMemory`。
