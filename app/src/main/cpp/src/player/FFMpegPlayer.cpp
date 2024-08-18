#include "FFMpegPlayer.h"
#include "VideoDecoder.h"
#include "AudioDecoder.h"

FFMpegPlayer::FFMpegPlayer()
{
}

FFMpegPlayer::~FFMpegPlayer()
{
    if (videoDecoder) {
        delete videoDecoder;
    }
    if (audioDecoder) {
        delete audioDecoder;
    }
}

void FFMpegPlayer::init(JNIEnv *jniEnv, jobject obj, const char *url)
{
//    videoDecoder = new VideoDecoder(url);
    audioDecoder = new AudioDecoder(url);
}

void FFMpegPlayer::setSurface(jobject surface)
{
//    if (videoDecoder) {
//        videoDecoder->setSurface(surface);
//    }
}

void FFMpegPlayer::start()
{
    if (audioDecoder) {
        audioDecoder->start();
    }
}

void FFMpegPlayer::stop()
{
    if (audioDecoder) {
        audioDecoder->start();
    }
}