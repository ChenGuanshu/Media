#pragma once

#include "Decoder.h"
#include "AudioRenderer.h"

#define AUDIO_INBUF_SIZE 20480
#define AUDIO_REFILL_THRESH 4096

class AudioDecoder : public Decoder
{
public:

    AudioDecoder(const char *url);

    void start() override;

    void stop() override;

    void setSurface(JNIEnv *env, jobject surface) override;

private:

    const char* src = nullptr;

    ~AudioDecoder();

    AVFormatContext *formatCxt = nullptr;

    AVCodecContext *codecCxt = nullptr;

    int audioIndex;

    const AVCodec *avCodec = nullptr;

    AVCodecParserContext *parse = nullptr;

    SwrContext *swrContext = nullptr;

    /*用于存放待解码的数据，AV_INPUT_BUFFER_PADDING_SIZE的值为64，指的是在输入比特流末尾额外分配的用于解码的所需字节数。
     * 这主要是因为一些比特流读取器做了优化处理，一次性读取32位或64位数据，所以内存空间额外加上这一部分的大小。*/
    uint8_t inbuf[AUDIO_INBUF_SIZE + AV_INPUT_BUFFER_PADDING_SIZE];

    FILE *f = nullptr;

    AVPacket *avPacket = nullptr;

    AVFrame *avFrame = nullptr;

    uint8_t *data;

    size_t data_size;

    size_t len;

    bool started = false;

    AudioRenderer *audioRenderer = nullptr;

};
