
#include <jni.h>
#include "AudioDecoder.h"
#include "Decoder.h"
#include "../Utils.h"

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

AudioDecoder::AudioDecoder(const char *url) : src(url)
{
    // 解封装
    if (avformat_open_input(&formatCxt, url, nullptr, nullptr) != 0) {
        throw "avformat_open_input failed";
    }

    // 获取音视频流
    if (avformat_find_stream_info(formatCxt, nullptr) != 0) {
        throw "avformat_find_stream_info failed";
    }

    AVCodecID codec_id;
    for (int i = 0; i < formatCxt->nb_streams; i++) {
        AVStream *avStream = formatCxt->streams[i];
        if (avStream->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            //在此获取音频流的编码id
            codec_id = avStream->codecpar->codec_id;
            audioIndex = i;
        }
    }

    if (!codec_id) {
        throw "find codec failed";
    }

    //根据音频的编码id查找解码器
    avCodec = avcodec_find_decoder(codec_id);
    parse = av_parser_init(avCodec->id);
    codecCxt = avcodec_alloc_context3(avCodec);
    avcodec_parameters_to_context(codecCxt, formatCxt->streams[audioIndex]->codecpar);
    int rst = 0;
    rst = avcodec_open2(codecCxt, avCodec, nullptr);
    if (rst != 0) {
        throw "avcodec_open2 failed";
    }
    LOGD("Loaded codec");

    //打开媒体文件，准备读入文件数据流，这里增加了Android 10使用fd打开文件的处理逻辑
    if (av_strstart(src, "file_fd:", &src)) {
        int fd = atoi(src);
        f = fdopen(fd, "rb");
    } else {
        f = fopen(src, "rb");
    }
    //从文件中读入数据流到inbuf中
    data_size = fread(inbuf, 1, AUDIO_INBUF_SIZE, f);
    data = inbuf;
    /*初始化重采样器，这里swr_alloc_set_opt方法设置重采样后的音频格式为S16,输出声道为立体声，采样频率为44100*/
    swrContext = swr_alloc();
    swr_alloc_set_opts(swrContext,
                       AV_CH_LAYOUT_STEREO,
                       AV_SAMPLE_FMT_S16,
                       44100,
                       codecCxt->channel_layout,
                       codecCxt->sample_fmt,
                       codecCxt->sample_rate,
                       0,
                       nullptr
    );
    swr_init(swrContext);

    avPacket = av_packet_alloc();
    avFrame = av_frame_alloc();

    audioRenderer = new AudioRenderer();

//    fail:
//    avformat_close_input(&formatCxt);
//    avcodec_free_context(&codecCxt);
//    av_packet_free(&avPacket);
//    if (!avFrame) {
//        av_frame_free(&avFrame);
//    }
}

void AudioDecoder::start()
{
    started = true;

    while (data_size > 0 && started) {
        /*将输入的数据封装到AVPacket中，其中av_parser_parse2的返回值ret表示数据流中已被封装的数据长度*/
        int ret = av_parser_parse2(parse,
                                   codecCxt,
                                   &avPacket->data,
                                   &avPacket->size,
                                   data,
                                   data_size,
                                   AV_NOPTS_VALUE,
                                   AV_NOPTS_VALUE,
                                   0);
        //数据指针向后移动到未使用的位置
        data += ret;
        //数据长度减去ret
        data_size -= ret;
        if (avPacket->size) {
            //用于存放解码后的数据
            auto *out_buffer = (uint8_t *) av_malloc(2 * 44100);
            //获取立体声声道数
            int outChannelCount = av_get_channel_layout_nb_channels(AV_CH_LAYOUT_STEREO);

            int rst = avcodec_send_packet(codecCxt, avPacket);
            while (rst >= 0) {
                //接收解码数据
                rst = avcodec_receive_frame(codecCxt, avFrame);
                if (rst == AVERROR(EAGAIN) || rst < 0 || rst == AVERROR(EOF)) {
                    break;
                }
                //对解码后的数据重采样，并将重采样的数据存放到out_buffer中
                swr_convert(swrContext, &out_buffer, 2 * 44100, (const uint8_t **) avFrame->data,
                            avFrame->nb_samples);
                //获取输出数据的buffer size
                int buffer_size = av_samples_get_buffer_size(nullptr, outChannelCount,
                                                             avFrame->nb_samples,
                                                             AV_SAMPLE_FMT_S16, 1);

                AudioFrame *audioFrame = new AudioFrame();
                audioFrame->buffer = out_buffer;
                audioFrame->buffer_size = buffer_size;
                if (audioRenderer != nullptr) {
                    audioRenderer->audioFrameQueue->push(audioFrame);
                    // TODO not right
                    audioRenderer->manualRender();
                }
            }
        }
        if (data_size < AUDIO_REFILL_THRESH) {
            /*如果inbuf的数据长度小于AUDIO_REFILL_THRESH，继续从文件中读取数据，将数据长度补充到AUDIO_INBUF_SIZE*/
            memmove(inbuf, data, data_size);
            data = inbuf;
            len = fread(data + data_size, 1,
                        AUDIO_INBUF_SIZE - data_size, f);
            if (len > 0)
                data_size += len;
        }
    }

    // TODO not right
    auto localRenderer = audioRenderer;
    if (localRenderer != nullptr) {
        while (!(localRenderer->audioFrameQueue->empty())) {
            localRenderer->manualRender();
        }
    }


    fclose(f);
    av_parser_close(parse);

}

void AudioDecoder::stop()
{
    started = false;
}

void AudioDecoder::setSurface(JNIEnv *env, jobject surface)
{
    LOGD("AudioDecoder::setSurface not supported");
}

AudioDecoder::~AudioDecoder()
{
    avformat_close_input(&formatCxt);
    avcodec_free_context(&codecCxt);
    av_packet_free(&avPacket);
    if (!avFrame) {
        av_frame_free(&avFrame);
    }
}
