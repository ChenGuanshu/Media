#pragma  once

#include <jni.h>

extern "C" {
#include "libavutil/time.h"
#include "libavutil/imgutils.h"
#include "libswscale/swscale.h"
#include "libavformat/avformat.h"
#include "libavfilter/avfilter.h"
#include "libavcodec/jni.h"
#include "libavcodec/avcodec.h"
#include "libavutil/avutil.h"
#include "libswresample/swresample.h"
#include "libavutil/avstring.h"
}

class Decoder
{
public:

    virtual void start() = 0;

    virtual void stop() = 0;

    virtual void setSurface(JNIEnv *env, jobject surface) = 0;

private:

    AVFormatContext *formatCxt = nullptr;

    AVCodecContext *codecCxt = nullptr;

};