#include <jni.h>
#include <android/native_window_jni.h>
#include "VideoDecoder.h"

VideoDecoder::VideoDecoder(JNIEnv *env, char *url)
{
    // TODO
}

void VideoDecoder::start()
{

}

void VideoDecoder::stop()
{

}

void VideoDecoder::setSurface(JNIEnv *env, jobject surface)
{
    m_NativeWindow = ANativeWindow_fromSurface(env, surface);
    ANativeWindow_setBuffersGeometry(
            m_NativeWindow,
            m_Width,
            m_Height,
            WINDOW_FORMAT_RGBA_8888
    );
}
