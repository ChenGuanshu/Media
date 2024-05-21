#include <jni.h>
#include <string>
#include <unistd.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

extern "C" {
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

void LOGD(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    __android_log_print(ANDROID_LOG_DEBUG, "QWER-native", fmt, args);
    va_end(args);
}

std::string CodecInfo(const AVCodec *codec) {
    char ret[200];
    std::string mediaType;
    switch (codec->type) {
        case AVMEDIA_TYPE_AUDIO:
            mediaType = "Audio";
            break;
        case AVMEDIA_TYPE_VIDEO:
            mediaType = "Video";
            break;
        default:
            mediaType = "Unknown";
            break;
    }
    sprintf(ret, "Codec Name:%s, Long Name:%s, Type:%s", codec->name, codec->long_name,
            mediaType.c_str());
    return ret;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_guanshu_media_FfmpegPlayerActivity_loadFfmpegInfo(JNIEnv *env, jobject thiz) {
    LOGD("start load ffmpeg");

    std::string info;
    const AVCodec *codec = NULL;
    void *opaque = NULL;

    // 使用av_codec_iterate迭代所有编解码器
    while ((codec = av_codec_iterate(&opaque)) != NULL) {
        info += CodecInfo(codec);
        info += "\n";
    }

    return env->NewStringUTF(info.c_str());
}

bool audioStop = false;

#define AUDIO_INBUF_SIZE 20480
#define AUDIO_REFILL_THRESH 4096

int decodeAudioV1(JNIEnv *env, jobject thiz, jstring file) {
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

    // 解封装
    if (avformat_open_input(&formatCxt, src, nullptr, nullptr) != 0) {
        LOGD("avformat_open_input failed");
        return -1;
    }

    // 获取音视频流
    if (avformat_find_stream_info(formatCxt, nullptr) != 0) {
        LOGD("avformat_find_stream_info failed");
        return -1;
    }

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
    LOGD("audioIndex=%d", audioIndex);

    if (!codec_id) {
        LOGD("no codec/audioIndex");
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
        LOGD("avcodec_open2 failed");
        goto fail;
    }
    LOGD("Loaded codec");
    LOGD(CodecInfo(avCodec).c_str());

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

int decodeAudioV2(JNIEnv *env, jobject thiz, jstring file) {
    audioStop = false;
    const char *src = env->GetStringUTFChars(file, 0);
    AVFormatContext *formatCxt = nullptr;
    AVCodecContext *codecCxt = nullptr;
    jclass activityClass = env->GetObjectClass(thiz);
    jmethodID onDataReceive = env->GetMethodID(activityClass, "onDataReceive", "([B)V");

    // 解封装
    if (avformat_open_input(&formatCxt, src, nullptr, nullptr) != 0) {
        return -1;
    }

    // 获取音视频流
    if (avformat_find_stream_info(formatCxt, nullptr) != 0) {
        return -1;
    }

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

    int ret;
    AVPacket *avPacket = av_packet_alloc();
    AVFrame *avFrame = av_frame_alloc();

    //根据音频的编码id查找解码器
    const AVCodec *avCodec = avcodec_find_decoder(codec_id);
    SwrContext *swrContext = nullptr;

    codecCxt = avcodec_alloc_context3(avCodec);
    avcodec_parameters_to_context(codecCxt, formatCxt->streams[audioIndex]->codecpar);
    int rst = 0;
    rst = avcodec_open2(codecCxt, avCodec, nullptr);
    if (rst != 0) {
        goto fail;
    }
    LOGD("Loaded codec");
    LOGD(CodecInfo(avCodec).c_str());

    /*初始化重采样器，这里swr_alloc_set_opt方法设置重采样后的音频格式为S16,输出声道为立体声，采样频率为44100*/
    swrContext = swr_alloc();
    swr_alloc_set_opts(swrContext, AV_CH_LAYOUT_STEREO, AV_SAMPLE_FMT_S16,
                       44100, codecCxt->channel_layout, codecCxt->sample_fmt, codecCxt->sample_rate,
                       0, nullptr);
    swr_init(swrContext);
    while (av_read_frame(formatCxt, avPacket) >= 0 && !audioStop) {
        if (avPacket->stream_index != audioIndex) {
            continue;
        }
        if (avcodec_send_packet(codecCxt, avPacket) != 0) {
            return -1;
        }
        //用于存放解码后的数据
        auto *out_buffer = (uint8_t *) av_malloc(2 * 44100);
        //获取立体声声道数
        int outChannelCount = av_get_channel_layout_nb_channels(AV_CH_LAYOUT_STEREO);

        while (avcodec_receive_frame(codecCxt, avFrame) == 0) {
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
            if (!audioStop) {
                env->CallVoidMethod(thiz, onDataReceive, arr);
            }
            env->DeleteLocalRef(arr);
        }

        av_packet_unref(avPacket);
    }

    fail:
    avformat_close_input(&formatCxt);
    avcodec_free_context(&codecCxt);
    av_packet_free(&avPacket);
    env->ReleaseStringUTFChars(file, src);
    if (!avFrame) {
        av_frame_free(&avFrame);
    }
    audioStop = false;

    return rst;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_guanshu_media_FfmpegPlayerActivity_decodeAudio(JNIEnv *env, jobject thiz, jstring file) {
    if (false) {
        LOGD("start decode audio v1");
        return decodeAudioV1(env, thiz, file);
    } else {
        LOGD("start decode audio v2");
        return decodeAudioV2(env, thiz, file);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_guanshu_media_FfmpegPlayerActivity_stopAudio(JNIEnv *env, jobject thiz) {
    audioStop = true;
}

bool mediaStop = false;

extern "C"
JNIEXPORT void JNICALL
Java_com_guanshu_media_FfmpegPlayerActivity_stopMedia(JNIEnv *env, jobject thiz) {
    mediaStop = true;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_guanshu_media_FfmpegPlayerActivity_decodeMedia(JNIEnv *env, jobject thiz, jstring file,
                                                        jobject surface) {
    LOGD("native decode media");
    mediaStop = false;
    const char *src = env->GetStringUTFChars(file, 0);
    AVFormatContext *formatCxt = nullptr;
    AVCodecContext *codecCxt = nullptr;
    jclass activityClass = env->GetObjectClass(thiz);
    jmethodID onDataReceive = env->GetMethodID(activityClass, "onDataReceive", "([B)V");

    // 解封装
    if (avformat_open_input(&formatCxt, src, nullptr, nullptr) != 0) {
        return -1;
    }

    // 获取音视频流
    if (avformat_find_stream_info(formatCxt, nullptr) != 0) {
        return -1;
    }

    AVCodecID codec_id;
    int videoIndex;
    for (int i = 0; i < formatCxt->nb_streams; i++) {
        AVStream *avStream = formatCxt->streams[i];
        if (avStream->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            //在此获取音频流的编码id
            codec_id = avStream->codecpar->codec_id;
            videoIndex = i;
        }
    }

    if (!codec_id) {
        return -1;
    }

    int ret;
    AVPacket *avPacket = av_packet_alloc();
    AVFrame *avFrame = av_frame_alloc();
    AVFrame *rgbaFrame = av_frame_alloc();

    //根据音频的编码id查找解码器
    const AVCodec *avCodec = avcodec_find_decoder(codec_id);

    codecCxt = avcodec_alloc_context3(avCodec);
    avcodec_parameters_to_context(codecCxt, formatCxt->streams[videoIndex]->codecpar);
    int rst = 0;
    rst = avcodec_open2(codecCxt, avCodec, nullptr);

    int videoWidth;
    int videoHeight;
    int bufferSize;
    uint8_t *frameBuffer = nullptr;
    SwsContext *swsContext = nullptr;

    ANativeWindow *nativeWindow;
    ANativeWindow_Buffer nativeWindowBuffer;

    if (rst != 0) {
        goto fail;
    }
    LOGD("Loaded codec");
    LOGD(CodecInfo(avCodec).c_str());

    videoWidth = codecCxt->width;
    videoHeight = codecCxt->height;
    LOGD("Resolution =%d,%d", videoWidth, videoHeight);

    nativeWindow = ANativeWindow_fromSurface(env, surface);
    ANativeWindow_setBuffersGeometry(nativeWindow, videoWidth, videoHeight,
                                     WINDOW_FORMAT_RGBA_8888);
    bufferSize = av_image_get_buffer_size(AV_PIX_FMT_RGBA, videoWidth, videoHeight, 1);
    frameBuffer = (uint8_t *) av_malloc(bufferSize * sizeof(uint8_t));

    swsContext = sws_getContext(videoWidth, videoHeight, codecCxt->pix_fmt,
                                videoWidth, videoHeight, AV_PIX_FMT_RGBA,
                                SWS_FAST_BILINEAR, NULL, NULL, NULL);

    while (av_read_frame(formatCxt, avPacket) >= 0 && !mediaStop) {
        if (avPacket->stream_index == videoIndex) {
            if (avcodec_send_packet(codecCxt, avPacket) != 0) {
                return -1;
            }
            while (avcodec_receive_frame(codecCxt, avFrame) == 0) {
                av_image_fill_arrays(rgbaFrame->data, rgbaFrame->linesize, frameBuffer,
                                     AV_PIX_FMT_RGBA,
                                     videoWidth, videoHeight, 1);

                sws_scale(swsContext, avFrame->data, avFrame->linesize, 0, videoHeight,
                          rgbaFrame->data,
                          rgbaFrame->linesize);

                ANativeWindow_lock(nativeWindow, &nativeWindowBuffer, nullptr);
                uint8_t *dstBuffer = static_cast<uint8_t *>(nativeWindowBuffer.bits);

                int srcLineSize = rgbaFrame->linesize[0];//输入图的步长（一行像素有多少字节）
                int dstLineSize = nativeWindowBuffer.stride * 4;//RGBA 缓冲区步长

                for (int i = 0; i < videoHeight; ++i) {
                    //一行一行地拷贝图像数据
                    memcpy(dstBuffer + i * dstLineSize, frameBuffer + i * srcLineSize, srcLineSize);
                }
                ANativeWindow_unlockAndPost(nativeWindow);
            }
            av_packet_unref(avPacket);
        }
    }
    LOGD("DONE playback");

    fail:
    avformat_close_input(&formatCxt);
    avcodec_free_context(&codecCxt);
    av_packet_free(&avPacket);
    env->ReleaseStringUTFChars(file, src);
    if (!swsContext) {
        sws_freeContext(swsContext);
    }
    if (!frameBuffer) {
        av_free(frameBuffer);
    }
    if (!avFrame) {
        av_frame_free(&avFrame);
    }
    if (!rgbaFrame) {
        av_frame_free(&rgbaFrame);
    }
    mediaStop = false;

    return rst;
}