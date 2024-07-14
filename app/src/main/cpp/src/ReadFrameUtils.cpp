#include "iostream"
#include "Utils.h"
#include <jni.h>
#include <android/bitmap.h>
#include <GLES2/gl2.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <EGL/egl.h>
#include <string.h>
#include <chrono>

void checkGlError(const char *op)
{
    for (GLint error = glGetError(); error; error = glGetError()) {
        LOGE("Call (%s) glError (0x%x)\n", op, error);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_guanshu_media_view_AdvancedOpenglSurfaceView_nativeReadPixel(
        JNIEnv *env,
        jobject thiz,
        jobject bitmap,
        jint width,
        jint height)
{
    unsigned char *pixels = new unsigned char[width * height * 4]; // RGBA, 4 bytes per pixel

    // 从OpenGL帧缓冲区读取像素数据
    glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

    if (bitmap == NULL) {
        delete[] pixels;
        return;
    }

    // 锁定Bitmap的像素区域以便写入
    AndroidBitmapInfo bitmapInfo;
    if (AndroidBitmap_getInfo(env, bitmap, &bitmapInfo) < 0) {
        delete[] pixels;
        return;
    }

    void *pixelsLocked;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixelsLocked) < 0) {
        delete[] pixels;
        return;
    }

    // 将从OpenGL读取的像素数据复制到Bitmap中
    memcpy(pixelsLocked, pixels, width * height * 4);

    // 解锁Bitmap的像素区域
    AndroidBitmap_unlockPixels(env, bitmap);

    // 清理OpenGL像素数据数组
    delete[] pixels;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_guanshu_media_view_AdvancedOpenglSurfaceView_nativeReadPBO(
        JNIEnv *env,
        jobject thiz,
        jobject bitmap,
        jint width,
        jint height)
{
    checkGlError("begin");

    AndroidBitmapInfo info;
    void *pixels = 0;
    GLuint pboIds[1];
    GLuint textureId;

    // 获取bitmap信息
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGD("Error getting bitmap info");
        return;
    }

    // 确保bitmap格式正确
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGD("Bitmap format is not RGBA_8888!");
        return;
    }

    // 锁定bitmap的像素缓冲区
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGD("Error locking pixels");
    }
    LOGD("bitmap width:%d, height:%d", info.width, info.height);

    // 创建PBO
    glGenBuffers(1, pboIds);
    glBindBuffer(GL_PIXEL_PACK_BUFFER, pboIds[0]);
    glBufferData(GL_PIXEL_PACK_BUFFER, info.width * info.height * 4, 0, GL_STREAM_READ);
    checkGlError("bind PBO");

    // 读取帧缓冲到PBO
    glReadPixels(0, 0, info.width, info.height, GL_RGBA, GL_UNSIGNED_BYTE, 0);
    checkGlError("readTo PBO");

    glBindBuffer(GL_PIXEL_PACK_BUFFER, pboIds[0]);
//    auto start = std::chrono::high_resolution_clock::now(); // this is not right for mapBufferRange
    GLubyte *ptr = static_cast<GLubyte *>(glMapBufferRange(GL_PIXEL_PACK_BUFFER, 0,
                                                           info.width * info.height * 4,
                                                           GL_MAP_READ_BIT));
    checkGlError("mapBufferRange");
//    auto end = std::chrono::high_resolution_clock::now();
//    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start);
//    LOGD("mapBufferRange cost:%lld ms", duration.count());

    // 将PBO中的数据拷贝到bitmap
    if (ptr) {
        memcpy(pixels, ptr, info.width * info.height * 4);
        glUnmapBuffer(GL_PIXEL_PACK_BUFFER);
    }
    checkGlError("unmapBuffer");

    // 清理
    glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
    glDeleteBuffers(1, pboIds);
    AndroidBitmap_unlockPixels(env, bitmap);

    checkGlError("end");
}