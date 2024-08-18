#pragma once

#include <android/native_window.h>
#include <jni.h>
#include "Decoder.h"

class VideoDecoder : public Decoder
{
public:

    VideoDecoder(JNIEnv *env, char *url);

    void start() override;

    void stop() override;

    void setSurface(JNIEnv *env, jobject surface) override;

private:

    ANativeWindow *m_NativeWindow;

    int32_t m_Width;

    int32_t m_Height;

};