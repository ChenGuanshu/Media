#include "EglCore.h"
#include "Utils.h"
#include <assert.h>
#include <EGL/egl.h>
#include <thread>
#include <iostream>
#include <sstream>

EglCore::EglCore() {
    init(NULL, 0);
}

EglCore::EglCore(EGLContext sharedContext, int flags) {
    init(sharedContext, flags);
}

EglCore::~EglCore() {
    release();
}

bool EglCore::init(EGLContext sharedContext, int flags) {
    assert(mEGLDisplay == EGL_NO_DISPLAY);
    if (mEGLDisplay != EGL_NO_DISPLAY) {
        LOGD("EGL already set up");
        return false;
    }
    if (sharedContext == NULL) {
        sharedContext = EGL_NO_CONTEXT;
    }

    mEGLDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    assert(mEGLDisplay != EGL_NO_DISPLAY);
    if (mEGLDisplay == EGL_NO_DISPLAY) {
        LOGD("unable to get EGL14 display.\n");
        return false;
    }

    if (!eglInitialize(mEGLDisplay, 0, 0)) {
        mEGLDisplay = EGL_NO_DISPLAY;
        LOGD("unable to initialize EGL14");
        return false;
    }

    if ((flags & FLAG_TRY_GLES3) != 0) {
        EGLConfig config = getConfig(flags, 3);
        if (config != NULL) {
            int attrib3_list[] = {
                    EGL_CONTEXT_CLIENT_VERSION, 3,
                    EGL_NONE
            };
            EGLContext context = eglCreateContext(
                    mEGLDisplay, config,
                    sharedContext, attrib3_list
            );
            checkEglError("eglCreateContext");
            if (eglGetError() == EGL_SUCCESS) {
                mEGLConfig = config;
                mEGLContext = context;
                mGlVersion = 3;
            }
        }
    }
    if (mEGLContext == EGL_NO_CONTEXT) {
        EGLConfig config = getConfig(flags, 2);
        assert(config != NULL);
        int attrib2_list[] = {
                EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL_NONE
        };
        EGLContext context = eglCreateContext(
                mEGLDisplay, config, sharedContext, attrib2_list
        );
        checkEglError("eglCreateContext");
        if (eglGetError() == EGL_SUCCESS) {
            mEGLConfig = config;
            mEGLContext = context;
            mGlVersion = 2;
        }
    }

    int values[1] = {0};
    eglQueryContext(mEGLDisplay, mEGLContext, EGL_CONTEXT_CLIENT_VERSION, values);
    if (!eglMakeCurrent(mEGLDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, mEGLContext)) {
        LOGD("init makeCurrent failed");
    }

    auto thread_id = std::this_thread::get_id();
    std::ostringstream oss;
    oss << thread_id;
    std::string thread_id_str = oss.str();
    LOGD("EGLContext created, client version %d, thread: %s", values[0], thread_id_str.c_str());

    return true;
}


/**
 * 获取合适的EGLConfig
 * @param flags
 * @param version
 * @return
 */
EGLConfig EglCore::getConfig(int flags, int version) {
    int renderableType = EGL_OPENGL_ES2_BIT;
    if (version >= 3) {
        renderableType |= EGL_OPENGL_ES3_BIT_KHR;
    }
    int attribList[] = {
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_DEPTH_SIZE, 8,
            EGL_STENCIL_SIZE, 0,
            EGL_RENDERABLE_TYPE, renderableType,
            EGL_NONE, 0,      // placeholder for recordable [@-3]
            EGL_NONE
    };
    int length = sizeof(attribList) / sizeof(attribList[0]);
    if ((flags & FLAG_RECORDABLE) != 0) {
        attribList[length - 3] = EGL_RECORDABLE_ANDROID;
        attribList[length - 2] = 1;
    }
    EGLConfig configs = NULL;
    int numConfigs;
    if (!eglChooseConfig(mEGLDisplay, attribList, &configs, 1, &numConfigs)) {
        LOGD("unable to find RGB8888 / %d  EGLConfig", version);
        return NULL;
    }
    return configs;
}

void EglCore::release() {
    LOGD("release EglCore");
    if (mEGLDisplay != EGL_NO_DISPLAY) {
        eglMakeCurrent(mEGLDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroyContext(mEGLDisplay, mEGLContext);
        eglReleaseThread();
        eglTerminate(mEGLDisplay);
    }

    mEGLDisplay = EGL_NO_DISPLAY;
    mEGLContext = EGL_NO_CONTEXT;
    mEGLConfig = NULL;
}

EGLContext EglCore::getEGLContext() {
    return mEGLContext;
}

void EglCore::releaseSurface(EGLSurface eglSurface) {
    eglDestroySurface(mEGLDisplay, eglSurface);
}

EGLSurface EglCore::createWindowSurface(ANativeWindow *surface) {
    assert(surface != NULL);
    if (surface == NULL) {
        LOGD("ANativeWindow is NULL!");
        return NULL;
    }
    int surfaceAttribs[] = {
            EGL_NONE
    };
    LOGD("eglCreateWindowSurface start");
    EGLSurface eglSurface = eglCreateWindowSurface(mEGLDisplay, mEGLConfig, surface,
                                                   surfaceAttribs);
    checkEglError("eglCreateWindowSurface");
    assert(eglSurface != NULL);
    if (eglSurface == NULL) {
        LOGD("EGLSurface is NULL!");
        return NULL;
    }
    return eglSurface;
}

EGLSurface EglCore::createOffscreenSurface(int width, int height) {
    int surfaceAttribs[] = {
            EGL_WIDTH, width,
            EGL_HEIGHT, height,
            EGL_NONE
    };
    EGLSurface eglSurface = eglCreatePbufferSurface(mEGLDisplay, mEGLConfig, surfaceAttribs);
    assert(eglSurface != NULL);
    if (eglSurface == NULL) {
        LOGD("Surface was null");
        return NULL;
    }
    return eglSurface;
}

void EglCore::makeCurrent(EGLSurface eglSurface) {
    if (mEGLDisplay == EGL_NO_DISPLAY) {
        LOGD("Note: makeCurrent w/o display.\n");
    }
    if (!eglMakeCurrent(mEGLDisplay, eglSurface, eglSurface, mEGLContext)) {
        LOGD("makeCurrent failed");
    }
}

void EglCore::makeCurrent(EGLSurface drawSurface, EGLSurface readSurface) {
    if (mEGLDisplay == EGL_NO_DISPLAY) {
        LOGD("Note: makeCurrent w/o display.\n");
    }
    if (!eglMakeCurrent(mEGLDisplay, drawSurface, readSurface, mEGLContext)) {
        LOGD("makeCurrent failed");
    }
}

void EglCore::makeNothingCurrent() {
    if (!eglMakeCurrent(mEGLDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT)) {
        LOGD("makeNothingCurrent failed");
    }
}

bool EglCore::swapBuffers(EGLSurface eglSurface) {
    return eglSwapBuffers(mEGLDisplay, eglSurface);
}

void EglCore::checkEglError(const char *msg) {
    int error;
    if ((error = eglGetError()) != EGL_SUCCESS) {
        LOGD("%s: EGL error: %x", msg, error);
    }
}