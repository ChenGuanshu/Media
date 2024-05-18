#include <jni.h>
#include <string>
#include <unistd.h>

extern "C" {
#include "libavformat/avformat.h"
#include "libavfilter/avfilter.h"
#include "libavcodec/jni.h"
#include "libavcodec/avcodec.h"
#include "libavutil/avutil.h"
#include "libswresample/swresample.h"
#include "libavutil/avstring.h"
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_guanshu_media_FfmpegPlayerActivity_loadFfmpegInfo(JNIEnv *env, jobject thiz) {
    std::string info;
    const AVCodec *codec = NULL;
    void *opaque = NULL;

    // 使用av_codec_iterate迭代所有编解码器
    while ((codec = av_codec_iterate(&opaque)) != NULL) {
        info += "Codec Name: ";
        info += codec->name;
        info += "\nCodec Long Name: ";
        info += codec->long_name;
        info += "\n";
    }

    return env->NewStringUTF(info.c_str());
}

#define AUDIO_INBUF_SIZE 20480
#define AUDIO_REFILL_THRESH 4096

extern "C"
JNIEXPORT jint JNICALL
Java_com_guanshu_media_FfmpegPlayerActivity_decodeAudio(JNIEnv *env, jobject thiz, jstring file) {
    const char *src = env->GetStringUTFChars(file, 0);
    AVFormatContext *formatCxt = nullptr;
    AVCodecContext *codecCxt = nullptr;
    /*用于存放待解码的数据，AV_INPUT_BUFFER_PADDING_SIZE的值为64，指的是在输入比特流末尾额外分配的用于解码的所需字节数。这主要是因为一些比特流读取器做了优化处理，一次性读取32位或64位数据，所以内存空间额外加上这一部分的大小。*/
    uint8_t inbuf[AUDIO_INBUF_SIZE + AV_INPUT_BUFFER_PADDING_SIZE];
    FILE *f = nullptr;
    AVPacket *avPacket = nullptr;
    avPacket = av_packet_alloc();
    AVFrame *avFrame = nullptr;
    int ret;
    uint8_t *data;
    size_t data_size, len;

    jclass activityClass = env->GetObjectClass(thiz);
    jmethodID onDataReceive = env->GetMethodID(activityClass, "onDataReceive", "([B)V");
    /*打开媒体文件，并查找文件的音频流，获取音频流的编码格式*/
    avformat_open_input(&formatCxt, src, nullptr, nullptr);
    avformat_find_stream_info(formatCxt, nullptr);
    AVCodecID codec_id;
    int audioIndex;
    for (int i = 0; i < formatCxt->nb_streams; i++) {
        AVStream *avStream = formatCxt->streams[i];
        if (avStream->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            //在此获取音频流的编码id
            codec_id = avStream->codecpar->codec_id;
            audioIndex = i;
        }
    }
    if (!codec_id) {
        return -1;
    }
    //根据音频的编码id查找解码器
    const AVCodec *avCodec = avcodec_find_decoder(codec_id);
    AVCodecParserContext *parse = nullptr;
    SwrContext *swrContext = nullptr;

    parse = av_parser_init(avCodec->id);
    codecCxt = avcodec_alloc_context3(avCodec);
    avcodec_parameters_to_context(codecCxt, formatCxt->streams[audioIndex]->codecpar);
    int rst = 0;
    rst = avcodec_open2(codecCxt, avCodec, nullptr);
    if (rst != 0) {
        goto fail;
    }
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
    swr_alloc_set_opts(swrContext, AV_CH_LAYOUT_STEREO, AV_SAMPLE_FMT_S16,
                       44100, codecCxt->channel_layout, codecCxt->sample_fmt, codecCxt->sample_rate,
                       0, nullptr);
    swr_init(swrContext);
    while (data_size > 0) {
        //进入解码的逻辑
        if (avFrame == nullptr) {
            avFrame = av_frame_alloc();
        }
        /*将输入的数据封装到AVPacket中，其中av_parser_parse2的返回值ret表示数据流中已被封装的数据长度*/
        ret = av_parser_parse2(parse, codecCxt, &avPacket->data, &avPacket->size, data, data_size,
                               AV_NOPTS_VALUE, AV_NOPTS_VALUE, 0);
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
                jbyteArray arr = env->NewByteArray(buffer_size);
                env->SetByteArrayRegion(arr, 0, buffer_size,
                                        reinterpret_cast<const jbyte *>(out_buffer));
                //将解码后的裸流输出到Java层
                env->CallVoidMethod(thiz, onDataReceive, arr);
                env->DeleteLocalRef(arr);
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

    fclose(f);
    av_parser_close(parse);

    fail:
    avformat_close_input(&formatCxt);
    avcodec_free_context(&codecCxt);
    av_packet_free(&avPacket);
    env->ReleaseStringUTFChars(file, src);
    if (!avFrame) {
        av_frame_free(&avFrame);
    }

    return rst;
}