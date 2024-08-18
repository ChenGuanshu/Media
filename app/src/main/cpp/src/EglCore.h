#pragma once

#include <android/native_window.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <EGL/eglplatform.h>

#define FLAG_TRY_GLES3 002
#define FLAG_RECORDABLE 0x01

class EglCore {

private:

    EGLDisplay mEGLDisplay = EGL_NO_DISPLAY;

    EGLConfig mEGLConfig = NULL;

    EGLContext mEGLContext = EGL_NO_CONTEXT;

    int mGlVersion = -1;

    EGLConfig getConfig(int flags, int version);

    bool init(EGLContext sharedContext, int flags);

public:

    EglCore();

    ~EglCore();

    EglCore(EGLContext sharedContext, int flags);

    void release();

    EGLContext getEGLContext();

    void releaseSurface(EGLSurface eglSurface);

    EGLSurface createWindowSurface(ANativeWindow *surface);

    EGLSurface createOffscreenSurface(int width, int height);

    void makeCurrent(EGLSurface eglSurface);

    void makeCurrent(EGLSurface drawSurface, EGLSurface readSurface);

    void makeNothingCurrent();

    bool swapBuffers(EGLSurface eglSurface);

    void checkEglError(const char *msg);

};
