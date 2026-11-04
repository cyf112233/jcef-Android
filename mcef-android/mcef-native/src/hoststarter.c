#include "mcef_proto.h"

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

/*
 * 从 native 里把插件 APK 的常驻服务拉起来，玩家就不用先手动打开插件 App。
 *
 * 前提：宿主进程里同时存在 ART。FCL 会把两个东西塞进环境变量：
 *   DALVIK_JAVAVM      ART 的 JavaVM 指针
 *   DALVIK_APPLICATION Application Context 的 jobject 指针
 * Pojav 没有这两个变量，这里就直接放弃，让玩家手动开一次。
 *
 * 全部走反射：插件 APK 的类不在启动器的 classpath 上，
 * 所以只能按字符串构造 Intent，不能 Class.forName。
 */

static JavaVM *art_vm(void) {
    const char *p = getenv("DALVIK_JAVAVM");
    if (!p || !*p) return NULL;
    return (JavaVM *)(intptr_t)strtoull(p, NULL, 0);
}

static jobject art_context(void) {
    const char *p = getenv("DALVIK_APPLICATION");
    if (!p || !*p) return NULL;
    return (jobject)(intptr_t)strtoull(p, NULL, 0);
}

static jclass find(JNIEnv *env, const char *name) {
    jclass c = (*env)->FindClass(env, name);
    if (!c && (*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    return c;
}

/** 返回 1 表示已发出启动请求。 */
int mcef_try_start_host(void) {
    JavaVM *vm = art_vm();
    jobject ctx = art_context();
    if (!vm || !ctx) return 0;

    JNIEnv *env = NULL;
    int attached = 0;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*vm)->AttachCurrentThread(vm, (void **)&env, NULL) != JNI_OK) return 0;
        attached = 1;
    }

    int started = 0;
    jclass intentCls = find(env, "android/content/Intent");
    jclass ctxCls = (*env)->GetObjectClass(env, ctx);
    if (intentCls && ctxCls) {
        jmethodID intentCtor = (*env)->GetMethodID(env, intentCls, "<init>", "()V");
        jmethodID setClass = (*env)->GetMethodID(env, intentCls, "setClassName",
                "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;");
        jmethodID startSvc = (*env)->GetMethodID(env, ctxCls, "startForegroundService",
                "(Landroid/content/Intent;)Landroid/content/ComponentName;");
        if (!startSvc) {
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
            startSvc = (*env)->GetMethodID(env, ctxCls, "startService",
                    "(Landroid/content/Intent;)Landroid/content/ComponentName;");
        }

        if (intentCtor && setClass && startSvc) {
            jobject intent = (*env)->NewObject(env, intentCls, intentCtor);
            jstring pkg = (*env)->NewStringUTF(env, "com.mcef.host");
            jstring cls = (*env)->NewStringUTF(env, "com.mcef.host.CefHostService");
            (*env)->CallObjectMethod(env, intent, setClass, pkg, cls);
            (*env)->CallObjectMethod(env, ctx, startSvc, intent);

            if (!(*env)->ExceptionCheck(env)) {
                started = 1;
            } else {
                /* 包可见性 / 权限被拦，属于正常情况，不抛给上层 */
                (*env)->ExceptionClear(env);
            }
            (*env)->DeleteLocalRef(env, intent);
            (*env)->DeleteLocalRef(env, pkg);
            (*env)->DeleteLocalRef(env, cls);
        }
    }

    if (attached) (*vm)->DetachCurrentThread(vm);
    return started;
}
