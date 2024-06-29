#include <jni.h>
#include <string>
#include <android/native_window_jni.h>
#include "Utils.h"
#include "EglCore.h"

extern "C"
JNIEXPORT jlong JNICALL
Java_com_guanshu_media_opengl_egl_EglManagerNative_nativeInit(
        JNIEnv *env, jobject thiz, jobject shared_egl_context) {
    EglCore *eglCore = new EglCore(NULL, FLAG_TRY_GLES3);
    return reinterpret_cast<jlong>(eglCore);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_guanshu_media_opengl_egl_EglManagerNative_nativeInitEglSurface(
        JNIEnv *env,
        jobject thiz,
        jlong native_egl,
        jobject surface) {
    auto eglCore = reinterpret_cast<EglCore *>(native_egl);
    auto *nativeWindow = ANativeWindow_fromSurface(env, surface);
    auto eglSurface = eglCore->createWindowSurface(nativeWindow);
    return reinterpret_cast<jlong>(eglSurface);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_guanshu_media_opengl_egl_EglManagerNative_nativeMakeEglCurrent(
        JNIEnv *env,
        jobject thiz,
        jlong native_egl,
        jlong native_egl_surface) {
    auto eglCore = reinterpret_cast<EglCore *>(native_egl);
    auto eglSurface = reinterpret_cast<EGLSurface *>(native_egl_surface);
    eglCore->makeCurrent(eglSurface);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_guanshu_media_opengl_egl_EglManagerNative_nativeMakeUnEglCurrent(
        JNIEnv *env,
        jobject thiz,
        jlong native_egl) {
    auto *eglCore = reinterpret_cast<EglCore *>(native_egl);
    eglCore->makeNothingCurrent();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_guanshu_media_opengl_egl_EglManagerNative_nativeSwapBuffer(
        JNIEnv *env,
        jobject thiz,
        jlong native_egl,
        jlong native_egl_surface) {
    auto *eglCore = reinterpret_cast<EglCore *>(native_egl);
    auto eglSurface = reinterpret_cast<EGLSurface *>(native_egl_surface);
    eglCore->swapBuffers(eglSurface);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_guanshu_media_opengl_egl_EglManagerNative_nativeReleaseEglSurface(
        JNIEnv *env,
        jobject thiz,
        jlong native_egl,
        jlong native_egl_surface) {
    EglCore *eglCore = reinterpret_cast<EglCore *>(native_egl);
    auto eglSurface = reinterpret_cast<EGLSurface *>(native_egl_surface);
    eglCore->releaseSurface(eglSurface);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_guanshu_media_opengl_egl_EglManagerNative_nativeRelease(
        JNIEnv *env,
        jobject thiz,
        jlong native_egl) {
    EglCore *eglCore = reinterpret_cast<EglCore *>(native_egl);
    eglCore->release();
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_guanshu_media_opengl_egl_EglManagerNative_nativeGetEglContext(
        JNIEnv *env,
        jobject thiz,
        jlong native_egl) {
    EglCore *eglCore = reinterpret_cast<EglCore *>(native_egl);
    auto eglContext = eglCore->getEGLContext();
    return reinterpret_cast<jlong>(eglContext);
}