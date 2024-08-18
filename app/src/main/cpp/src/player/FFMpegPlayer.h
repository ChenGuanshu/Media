#pragma once

#include <jni.h>
#include "Decoder.h"

class FFMpegPlayer
{
public:
    FFMpegPlayer();

    void init(JNIEnv *jniEnv, jobject obj, const char *url);

    void setSurface(jobject surface);

    void start();

    void stop();

    ~FFMpegPlayer();

private:

    Decoder *videoDecoder = nullptr;

    Decoder *audioDecoder = nullptr;

};