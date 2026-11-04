#include "mcef_proto.h"

#include <jni.h>
#include <stdlib.h>
#include <string.h>

static JavaVM *g_vm;
static jclass  g_bridge;
static jstring g_utf8;

static jmethodID m_paint, m_popup_paint, m_popup_show, m_popup_size, m_cursor,
                 m_load_state, m_address, m_title, m_status, m_console,
                 m_before_popup, m_js_result, m_after_created, m_focused_node,
                 m_selection, m_render_terminated, m_download, m_context_menu,
                 m_disconnected, m_query, m_scheme_request;

static jclass    g_string_cls;
static jmethodID g_string_ctor;

static jstring to_jstring(JNIEnv *env, const char *s, size_t n) {
    if (!s) return NULL;
    jbyteArray arr = (*env)->NewByteArray(env, (jsize)n);
    if (!arr) return NULL;
    (*env)->SetByteArrayRegion(env, arr, 0, (jsize)n, (const jbyte *)s);
    jstring out = (jstring)(*env)->NewObject(env, g_string_cls, g_string_ctor, arr, g_utf8);
    (*env)->DeleteLocalRef(env, arr);
    return out;
}

static void cache_ids(JNIEnv *env, jclass cls) {
    if (g_bridge) return;
    g_bridge = (jclass)(*env)->NewGlobalRef(env, cls);

    g_string_cls = (jclass)(*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/String"));
    g_string_ctor = (*env)->GetMethodID(env, g_string_cls, "<init>", "([BLjava/lang/String;)V");
    jstring u = (*env)->NewStringUTF(env, "UTF-8");
    g_utf8 = (jstring)(*env)->NewGlobalRef(env, u);
    (*env)->DeleteLocalRef(env, u);

    m_paint            = (*env)->GetStaticMethodID(env, cls, "onPaint", "(JIIILjava/nio/ByteBuffer;)V");
    m_popup_paint      = (*env)->GetStaticMethodID(env, cls, "onPopupPaint", "(JIIILjava/nio/ByteBuffer;)V");
    m_popup_show       = (*env)->GetStaticMethodID(env, cls, "onPopupShow", "(JZ)V");
    m_popup_size       = (*env)->GetStaticMethodID(env, cls, "onPopupSize", "(JIIII)V");
    m_cursor           = (*env)->GetStaticMethodID(env, cls, "onCursorChange", "(JI)V");
    m_load_state       = (*env)->GetStaticMethodID(env, cls, "onLoadState", "(JZZZ)V");
    m_address          = (*env)->GetStaticMethodID(env, cls, "onAddressChange", "(JLjava/lang/String;)V");
    m_title            = (*env)->GetStaticMethodID(env, cls, "onTitleChange", "(JLjava/lang/String;)V");
    m_status           = (*env)->GetStaticMethodID(env, cls, "onStatusMessage", "(JLjava/lang/String;)V");
    m_console          = (*env)->GetStaticMethodID(env, cls, "onConsoleMessage", "(JILjava/lang/String;Ljava/lang/String;I)V");
    m_before_popup     = (*env)->GetStaticMethodID(env, cls, "onBeforePopup", "(JLjava/lang/String;)V");
    m_js_result        = (*env)->GetStaticMethodID(env, cls, "onJsResult", "(JIZLjava/lang/String;)V");
    m_after_created    = (*env)->GetStaticMethodID(env, cls, "onAfterCreated", "(JLjava/lang/String;)V");
    m_focused_node     = (*env)->GetStaticMethodID(env, cls, "onFocusedNode", "(JZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    m_selection        = (*env)->GetStaticMethodID(env, cls, "onSelectionChanged", "(JLjava/lang/String;)V");
    m_render_terminated= (*env)->GetStaticMethodID(env, cls, "onRenderTerminated", "(JI)V");
    m_download         = (*env)->GetStaticMethodID(env, cls, "onDownloadBegin", "(JLjava/lang/String;Ljava/lang/String;J)V");
    m_context_menu     = (*env)->GetStaticMethodID(env, cls, "onContextMenu", "(JIILjava/lang/String;Ljava/lang/String;I)V");
    m_disconnected     = (*env)->GetStaticMethodID(env, cls, "onDisconnected", "(J)V");
    m_query            = (*env)->GetStaticMethodID(env, cls, "onQuery",
                            "(JJZLjava/lang/String;Ljava/lang/String;)V");
    m_scheme_request   = (*env)->GetStaticMethodID(env, cls, "onSchemeRequest",
                            "(JILjava/lang/String;Ljava/lang/String;)V");
}

/* ---------- rx 线程 -> Java ---------- */

void mcef_dispatch(mcef_session *s, uint16_t type, uint32_t id, mcef_reader *r, int32_t *out_err) {
    if (!g_vm) return;
    JNIEnv *env = NULL;
    int attached = 0;
    if ((*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*g_vm)->AttachCurrentThread(g_vm, (void **)&env, NULL) != JNI_OK) return;
        attached = 1;
    }

    jlong h = (jlong)(intptr_t)mcef_get_user(s);

    switch (type) {
    case EV_PAINT:
    case EV_POPUP_PAINT: {
        uint32_t seq = mcef_rd_u32(r);
        uint32_t w   = mcef_rd_u32(r);
        uint32_t hgt = mcef_rd_u32(r);
        uint32_t stride = mcef_rd_u32(r);
        size_t need = (size_t)stride * hgt;
        if (w == 0 || hgt == 0 || stride < w * 4 || need > (64u << 20) ||
            r->pos + need > r->len) {
            *out_err = MCEF_ERR_INTERNAL;
            break;
        }
        uint8_t *dst = mcef_frame_scratch(s, need);
        if (!dst) { *out_err = MCEF_ERR_INTERNAL; break; }
        memcpy(dst, r->data + r->pos, need);

        jobject bb = (*env)->NewDirectByteBuffer(env, dst, (jlong)need);
        jmethodID mid = (type == EV_PAINT) ? m_paint : m_popup_paint;
        (*env)->CallStaticVoidMethod(env, g_bridge, mid, h, (jint)seq, (jint)w, (jint)hgt, bb);
        (*env)->DeleteLocalRef(env, bb);
        break;
    }
    case EV_POPUP_SHOW:
        (*env)->CallStaticVoidMethod(env, g_bridge, m_popup_show, h, (jboolean)(mcef_rd_u8(r) != 0));
        break;
    case EV_POPUP_SIZE:
        (*env)->CallStaticVoidMethod(env, g_bridge, m_popup_size, h,
            (jint)mcef_rd_u32(r), (jint)mcef_rd_u32(r), (jint)mcef_rd_u32(r), (jint)mcef_rd_u32(r));
        break;
    case EV_CURSOR_CHANGE:
        (*env)->CallStaticVoidMethod(env, g_bridge, m_cursor, h, (jint)mcef_rd_i32(r));
        break;
    case EV_LOAD_STATE: {
        jboolean loading = mcef_rd_u8(r) != 0;
        jboolean back = mcef_rd_u8(r) != 0;
        jboolean fwd = mcef_rd_u8(r) != 0;
        (*env)->CallStaticVoidMethod(env, g_bridge, m_load_state, h, loading, back, fwd);
        break;
    }
    case EV_ADDRESS_CHANGE: case EV_TITLE_CHANGE: case EV_STATUS_MESSAGE: case EV_BEFORE_POPUP: {
        char *str = mcef_rd_str(r);
        jstring js = to_jstring(env, str, str ? strlen(str) : 0);
        jmethodID mid = type == EV_ADDRESS_CHANGE ? m_address
                      : type == EV_TITLE_CHANGE   ? m_title
                      : type == EV_STATUS_MESSAGE ? m_status
                                                  : m_before_popup;
        (*env)->CallStaticVoidMethod(env, g_bridge, mid, h, js);
        if (js) (*env)->DeleteLocalRef(env, js);
        free(str);
        break;
    }
    case EV_CONSOLE_MESSAGE: {
        jint level = (jint)mcef_rd_i32(r);
        char *msg = mcef_rd_str(r);
        char *src = mcef_rd_str(r);
        jint line = (jint)mcef_rd_i32(r);
        jstring jm = to_jstring(env, msg, msg ? strlen(msg) : 0);
        jstring jsrc = to_jstring(env, src, src ? strlen(src) : 0);
        (*env)->CallStaticVoidMethod(env, g_bridge, m_console, h, level, jm, jsrc, line);
        if (jm) (*env)->DeleteLocalRef(env, jm);
        if (jsrc) (*env)->DeleteLocalRef(env, jsrc);
        free(msg); free(src);
        break;
    }
    case EV_JS_RESULT: {
        jboolean ok = mcef_rd_u8(r) != 0;
        char *res = mcef_rd_str(r);
        jstring jr = to_jstring(env, res, res ? strlen(res) : 0);
        (*env)->CallStaticVoidMethod(env, g_bridge, m_js_result, h, (jint)id, ok, jr);
        if (jr) (*env)->DeleteLocalRef(env, jr);
        free(res);
        break;
    }
    case EV_AFTER_CREATED: {
        char *url = mcef_rd_str(r);
        jstring ju = to_jstring(env, url, url ? strlen(url) : 0);
        (*env)->CallStaticVoidMethod(env, g_bridge, m_after_created, h, ju);
        if (ju) (*env)->DeleteLocalRef(env, ju);
        free(url);
        break;
    }
    case EV_FOCUSED_NODE: {
        jboolean editable = mcef_rd_u8(r) != 0;
        char *v = mcef_rd_str(r);
        char *p = mcef_rd_str(r);
        char *t = mcef_rd_str(r);
        jstring jv = to_jstring(env, v, v ? strlen(v) : 0);
        jstring jp = to_jstring(env, p, p ? strlen(p) : 0);
        jstring jt = to_jstring(env, t, t ? strlen(t) : 0);
        (*env)->CallStaticVoidMethod(env, g_bridge, m_focused_node, h, editable, jv, jp, jt);
        if (jv) (*env)->DeleteLocalRef(env, jv);
        if (jp) (*env)->DeleteLocalRef(env, jp);
        if (jt) (*env)->DeleteLocalRef(env, jt);
        free(v); free(p); free(t);
        break;
    }
    case EV_SELECTION: {
        char *text = mcef_rd_str(r);
        jstring jt = to_jstring(env, text, text ? strlen(text) : 0);
        (*env)->CallStaticVoidMethod(env, g_bridge, m_selection, h, jt);
        if (jt) (*env)->DeleteLocalRef(env, jt);
        free(text);
        break;
    }
    case EV_RENDER_TERMINATED:
        (*env)->CallStaticVoidMethod(env, g_bridge, m_render_terminated, h, (jint)mcef_rd_i32(r));
        break;
    case EV_DOWNLOAD_BEGIN: {
        char *url = mcef_rd_str(r);
        char *name = mcef_rd_str(r);
        jlong total = (jlong)mcef_rd_i64(r);
        jstring ju = to_jstring(env, url, url ? strlen(url) : 0);
        jstring jn = to_jstring(env, name, name ? strlen(name) : 0);
        (*env)->CallStaticVoidMethod(env, g_bridge, m_download, h, ju, jn, total);
        if (ju) (*env)->DeleteLocalRef(env, ju);
        if (jn) (*env)->DeleteLocalRef(env, jn);
        free(url); free(name);
        break;
    }
    case EV_CONTEXT_MENU: {
        jint x = (jint)mcef_rd_u32(r), y = (jint)mcef_rd_u32(r);
        char *link = mcef_rd_str(r);
        char *page = mcef_rd_str(r);
        jint kind = (jint)mcef_rd_u8(r);
        jstring jl = to_jstring(env, link, link ? strlen(link) : 0);
        jstring jp = to_jstring(env, page, page ? strlen(page) : 0);
        (*env)->CallStaticVoidMethod(env, g_bridge, m_context_menu, h, x, y, jl, jp, kind);
        if (jl) (*env)->DeleteLocalRef(env, jl);
        if (jp) (*env)->DeleteLocalRef(env, jp);
        free(link); free(page);
        break;
    }
    case EV_QUERY: {
        jlong queryId = (jlong)mcef_rd_i64(r);
        jboolean persistent = mcef_rd_u8(r) != 0;
        char *origin = mcef_rd_str(r);
        char *request = mcef_rd_str(r);
        jstring jo = to_jstring(env, origin, origin ? strlen(origin) : 0);
        jstring jr = to_jstring(env, request, request ? strlen(request) : 0);
        (*env)->CallStaticVoidMethod(env, g_bridge, m_query, h, queryId, persistent, jo, jr);
        if (jo) (*env)->DeleteLocalRef(env, jo);
        if (jr) (*env)->DeleteLocalRef(env, jr);
        free(origin); free(request);
        break;
    }
    case EV_SCHEME_REQUEST: {
        jint reqId = (jint)mcef_rd_u32(r);
        char *url = mcef_rd_str(r);
        char *method = mcef_rd_str(r);
        jstring ju = to_jstring(env, url, url ? strlen(url) : 0);
        jstring jm = to_jstring(env, method, method ? strlen(method) : 0);
        (*env)->CallStaticVoidMethod(env, g_bridge, m_scheme_request, h, reqId, ju, jm);
        if (ju) (*env)->DeleteLocalRef(env, ju);
        if (jm) (*env)->DeleteLocalRef(env, jm);
        free(url); free(method);
        break;
    }
    case EV_DISCONNECTED:
        (*env)->CallStaticVoidMethod(env, g_bridge, m_disconnected, h);
        break;
    case EV_ACK:
    case EV_PONG:
        break;
    default:
        /* 未知事件：让上层回 ACK(err)，连接保持 */
        *out_err = MCEF_ERR_INTERNAL;
        break;
    }

    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        *out_err = MCEF_ERR_INTERNAL;
    }
    if (attached) (*g_vm)->DetachCurrentThread(g_vm);
}

/* ---------- Java -> H ---------- */

#define S(ptr) ((mcef_session *)(intptr_t)(ptr))

JNIEXPORT jlong JNICALL n_connect(JNIEnv *env, jclass cls) {
    cache_ids(env, cls);
    mcef_session *s = mcef_connect();
    if (!s) return 0;
    mcef_set_user(s, s);
    return (jlong)(intptr_t)s;
}

JNIEXPORT void JNICALL n_close(JNIEnv *env, jclass cls, jlong h) {
    (void)env; (void)cls;
    if (h) mcef_close(S(h));
}

JNIEXPORT jint JNICALL n_create(JNIEnv *env, jclass cls, jlong h, jint w, jint hgt, jfloat dsf,
                                jboolean transparent, jstring url) {
    (void)cls;
    if (!h) return MCEF_ERR_CLOSED;
    const char *u = url ? (*env)->GetStringUTFChars(env, url, NULL) : NULL;
    mcef_buf b; mcef_buf_init(&b);
    mcef_buf_u32(&b, (uint32_t)w);
    mcef_buf_u32(&b, (uint32_t)hgt);
    mcef_buf_f32(&b, dsf);
    mcef_buf_u8(&b, transparent ? 1 : 0);
    mcef_buf_str(&b, u);
    int rc = mcef_send(S(h), OP_CREATE_BROWSER, 0, 0, &b);
    mcef_buf_free(&b);
    if (u) (*env)->ReleaseStringUTFChars(env, url, u);
    return rc;
}

JNIEXPORT void JNICALL n_destroy(JNIEnv *e, jclass c, jlong h) { (void)e;(void)c; if (h) mcef_send(S(h), OP_DESTROY, 0, 0, NULL); }
JNIEXPORT void JNICALL n_go_back(JNIEnv *e, jclass c, jlong h) { (void)e;(void)c; if (h) mcef_send(S(h), OP_GO_BACK, 0, 0, NULL); }
JNIEXPORT void JNICALL n_go_forward(JNIEnv *e, jclass c, jlong h) { (void)e;(void)c; if (h) mcef_send(S(h), OP_GO_FORWARD, 0, 0, NULL); }
JNIEXPORT void JNICALL n_stop(JNIEnv *e, jclass c, jlong h) { (void)e;(void)c; if (h) mcef_send(S(h), OP_STOP, 0, 0, NULL); }

JNIEXPORT void JNICALL n_load_url(JNIEnv *env, jclass cls, jlong h, jstring url) {
    (void)cls;
    if (!h || !url) return;
    const char *u = (*env)->GetStringUTFChars(env, url, NULL);
    mcef_buf b; mcef_buf_init(&b);
    mcef_buf_str(&b, u);
    mcef_send(S(h), OP_LOAD_URL, 0, 0, &b);
    mcef_buf_free(&b);
    (*env)->ReleaseStringUTFChars(env, url, u);
}

JNIEXPORT void JNICALL n_resize(JNIEnv *env, jclass cls, jlong h, jint w, jint hgt, jfloat dsf) {
    (void)env; (void)cls;
    if (!h) return;
    mcef_buf b; mcef_buf_init(&b);
    mcef_buf_u32(&b, (uint32_t)w);
    mcef_buf_u32(&b, (uint32_t)hgt);
    mcef_buf_f32(&b, dsf);
    mcef_send(S(h), OP_RESIZE, 0, 0, &b);
    mcef_buf_free(&b);
}

JNIEXPORT void JNICALL n_reload(JNIEnv *env, jclass cls, jlong h, jboolean ignore) {
    (void)env; (void)cls;
    if (!h) return;
    mcef_buf b; mcef_buf_init(&b);
    mcef_buf_u8(&b, ignore ? 1 : 0);
    mcef_send(S(h), OP_RELOAD, 0, 0, &b);
    mcef_buf_free(&b);
}

/* action: 0 move, 1 down, 2 up, 3 leave */
JNIEXPORT void JNICALL n_mouse(JNIEnv *env, jclass cls, jlong h, jint x, jint y,
                               jint mods, jint button, jint count, jint action) {
    (void)env; (void)cls;
    if (!h) return;
    mcef_buf b; mcef_buf_init(&b);
    mcef_buf_i32(&b, x);
    mcef_buf_i32(&b, y);
    mcef_buf_u32(&b, (uint32_t)mods);
    uint16_t op;
    if (action == 1 || action == 2) {
        mcef_buf_u8(&b, (uint8_t)button);
        mcef_buf_u16(&b, (uint16_t)count);
        op = action == 1 ? OP_MOUSE_DOWN : OP_MOUSE_UP;
    } else {
        mcef_buf_u8(&b, action == 3 ? 1 : 0);
        op = OP_MOUSE_MOVE;
    }
    mcef_send(S(h), op, 0, 0, &b);
    mcef_buf_free(&b);
}

JNIEXPORT void JNICALL n_wheel(JNIEnv *env, jclass cls, jlong h, jint x, jint y,
                               jint mods, jint dx, jint dy) {
    (void)env; (void)cls;
    if (!h) return;
    mcef_buf b; mcef_buf_init(&b);
    mcef_buf_i32(&b, x); mcef_buf_i32(&b, y);
    mcef_buf_u32(&b, (uint32_t)mods);
    mcef_buf_i32(&b, dx); mcef_buf_i32(&b, dy);
    mcef_send(S(h), OP_MOUSE_WHEEL, 0, 0, &b);
    mcef_buf_free(&b);
}

JNIEXPORT void JNICALL n_key(JNIEnv *env, jclass cls, jlong h, jint kind, jint key,
                             jint scan, jint mods, jint chr) {
    (void)env; (void)cls;
    if (!h) return;
    mcef_buf b; mcef_buf_init(&b);
    mcef_buf_u8(&b, (uint8_t)kind);
    mcef_buf_i32(&b, key);
    mcef_buf_i32(&b, scan);
    mcef_buf_u32(&b, (uint32_t)mods);
    mcef_buf_u16(&b, (uint16_t)chr);
    mcef_send(S(h), OP_KEY, 0, 0, &b);
    mcef_buf_free(&b);
}

JNIEXPORT void JNICALL n_focus(JNIEnv *env, jclass cls, jlong h, jboolean focused) {
    (void)env; (void)cls;
    if (!h) return;
    mcef_buf b; mcef_buf_init(&b);
    mcef_buf_u8(&b, focused ? 1 : 0);
    mcef_send(S(h), OP_SET_FOCUS, 0, 0, &b);
    mcef_buf_free(&b);
}

JNIEXPORT void JNICALL n_ime(JNIEnv *env, jclass cls, jlong h, jint kind, jstring text,
                             jint a, jint b2, jint c) {
    (void)cls;
    if (!h) return;
    const char *t = text ? (*env)->GetStringUTFChars(env, text, NULL) : NULL;
    mcef_buf b; mcef_buf_init(&b);
    if (kind == 1) {
        mcef_buf_str(&b, t);
        mcef_buf_i32(&b, a); mcef_buf_i32(&b, b2); mcef_buf_i32(&b, c);
        mcef_send(S(h), OP_IME_COMPOSE, 0, 0, &b);
    } else if (kind == 2) {
        mcef_buf_str(&b, t);
        mcef_send(S(h), OP_IME_COMMIT, 0, 0, &b);
    } else {
        mcef_send(S(h), OP_IME_CANCEL, 0, 0, NULL);
    }
    mcef_buf_free(&b);
    if (t) (*env)->ReleaseStringUTFChars(env, text, t);
}

JNIEXPORT void JNICALL n_eval_js(JNIEnv *env, jclass cls, jlong h, jint id, jstring code) {
    (void)cls;
    if (!h || !code) return;
    const char *c = (*env)->GetStringUTFChars(env, code, NULL);
    mcef_buf b; mcef_buf_init(&b);
    mcef_buf_str(&b, c);
    mcef_send(S(h), OP_EVAL_JS, 0, (uint32_t)id, &b);
    mcef_buf_free(&b);
    (*env)->ReleaseStringUTFChars(env, code, c);
}

JNIEXPORT void JNICALL n_zoom(JNIEnv *env, jclass cls, jlong h, jdouble level) {
    (void)env; (void)cls;
    if (!h) return;
    mcef_buf b; mcef_buf_init(&b);
    mcef_buf_f64(&b, level);
    mcef_send(S(h), OP_SET_ZOOM, 0, 0, &b);
    mcef_buf_free(&b);
}

JNIEXPORT void JNICALL n_background(JNIEnv *env, jclass cls, jlong h, jint argb) {
    (void)env; (void)cls;
    if (!h) return;
    mcef_buf b; mcef_buf_init(&b);
    mcef_buf_u32(&b, (uint32_t)argb);
    mcef_send(S(h), OP_SET_BACKGROUND, 0, 0, &b);
    mcef_buf_free(&b);
}

JNIEXPORT jboolean JNICALL n_ping(JNIEnv *env, jclass cls, jlong h) {
    (void)env; (void)cls;
    if (!h) return JNI_FALSE;
    mcef_buf b; mcef_buf_init(&b);
    mcef_buf_u32(&b, 1);
    int rc = mcef_send(S(h), OP_PING, 0, 0, &b);
    mcef_buf_free(&b);
    return rc == MCEF_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL n_query_reply(JNIEnv *env, jclass cls, jlong h, jlong queryId,
                                     jboolean ok, jint err, jstring response) {
    (void)cls;
    if (!h) return;
    const char *s = response ? (*env)->GetStringUTFChars(env, response, NULL) : NULL;
    mcef_buf b; mcef_buf_init(&b);
    mcef_buf_i64(&b, (int64_t)queryId);
    mcef_buf_u8(&b, ok ? 1 : 0);
    mcef_buf_i32(&b, err);
    mcef_buf_str(&b, s);
    mcef_send(S(h), OP_QUERY_REPLY, 0, 0, &b);
    mcef_buf_free(&b);
    if (s) (*env)->ReleaseStringUTFChars(env, response, s);
}

JNIEXPORT void JNICALL n_scheme_response(JNIEnv *env, jclass cls, jlong h, jint reqId,
                                         jboolean found, jint status, jstring mime, jbyteArray body) {
    (void)cls;
    if (!h) return;
    const char *m = mime ? (*env)->GetStringUTFChars(env, mime, NULL) : NULL;
    jsize n = body ? (*env)->GetArrayLength(env, body) : 0;
    jbyte *bytes = NULL;
    if (n > 0) bytes = (*env)->GetByteArrayElements(env, body, NULL);

    mcef_buf b; mcef_buf_init(&b);
    mcef_buf_u32(&b, (uint32_t)reqId);
    mcef_buf_u8(&b, found ? 1 : 0);
    mcef_buf_i32(&b, status);
    mcef_buf_str(&b, m);
    mcef_buf_u32(&b, (uint32_t)n);
    mcef_buf_bytes(&b, bytes, (size_t)n);
    mcef_send(S(h), OP_SCHEME_RESPONSE, 0, 0, &b);
    mcef_buf_free(&b);

    if (bytes) (*env)->ReleaseByteArrayElements(env, body, bytes, JNI_ABORT);
    if (m) (*env)->ReleaseStringUTFChars(env, mime, m);
}

static const JNINativeMethod kMethods[] = {
    { "nativeConnect",   "()J",                                  (void *)n_connect },
    { "nativeClose",     "(J)V",                                 (void *)n_close },
    { "nativeCreate",    "(JIIFZLjava/lang/String;)I",            (void *)n_create },
    { "nativeDestroy",   "(J)V",                                 (void *)n_destroy },
    { "nativeLoadUrl",   "(JLjava/lang/String;)V",               (void *)n_load_url },
    { "nativeResize",    "(JIIF)V",                              (void *)n_resize },
    { "nativeGoBack",    "(J)V",                                 (void *)n_go_back },
    { "nativeGoForward", "(J)V",                                 (void *)n_go_forward },
    { "nativeReload",    "(JZ)V",                                (void *)n_reload },
    { "nativeStop",      "(J)V",                                 (void *)n_stop },
    { "nativeMouse",     "(JIIIIII)V",                           (void *)n_mouse },
    { "nativeWheel",     "(JIIIII)V",                            (void *)n_wheel },
    { "nativeKey",       "(JIIIII)V",                            (void *)n_key },
    { "nativeFocus",     "(JZ)V",                                (void *)n_focus },
    { "nativeIme",       "(JILjava/lang/String;III)V",           (void *)n_ime },
    { "nativeEvalJs",    "(JILjava/lang/String;)V",              (void *)n_eval_js },
    { "nativeZoom",      "(JD)V",                                (void *)n_zoom },
    { "nativeBackground","(JI)V",                                (void *)n_background },
    { "nativePing",      "(J)Z",                                 (void *)n_ping },
    { "nativeQueryReply", "(JJZILjava/lang/String;)V",            (void *)n_query_reply },
    { "nativeSchemeResponse", "(JIZILjava/lang/String;[B)V",      (void *)n_scheme_response },
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    g_vm = vm;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;

    jclass cls = (*env)->FindClass(env, "com/cinemamod/mcef/nativebridge/McefNative");
    if (!cls) return JNI_ERR;
    if ((*env)->RegisterNatives(env, cls, kMethods,
                                (jint)(sizeof kMethods / sizeof kMethods[0])) != 0) return JNI_ERR;
    cache_ids(env, cls);
    (*env)->DeleteLocalRef(env, cls);
    return JNI_VERSION_1_6;
}
