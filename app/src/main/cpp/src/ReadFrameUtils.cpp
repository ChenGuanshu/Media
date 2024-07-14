#include "iostream"
#include <jni.h>
#include <android/bitmap.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

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