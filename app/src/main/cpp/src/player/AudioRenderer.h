#pragma once

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <queue>
#include <memory>
#include "../Utils.h"

struct AudioFrame
{
    uint8_t *buffer;
    int buffer_size;
};

class AudioRenderer
{
public:
    AudioRenderer()
    {
        // 1. 创建引擎并获取引擎接口
        SLresult result;
        // 1.1 创建引擎对象：SLObjectItf engineObject
        result = slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL);
        if (SL_RESULT_SUCCESS != result) {
            return;
        }
        // 1.2 初始化引擎
        result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
        if (SL_RESULT_SUCCESS != result) {
            return;
        }
        // 1.3 获取引擎接口 SLEngineItf engineInterface
        result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineInterface);
        if (SL_RESULT_SUCCESS != result) {
            return;
        }

        // 2.1 创建混音器：SLObjectItf outputMixObject
        result = (*engineInterface)->CreateOutputMix(engineInterface, &outputMixObject, 0, 0, 0);
        if (SL_RESULT_SUCCESS != result) {
            return;
        }
        // 2.2 初始化混音器
        result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
        if (SL_RESULT_SUCCESS != result) {
            return;
        }

        /**
         * 3、创建播放器
         */
        //3.1 配置输入声音信息
        //创建buffer缓冲类型的队列 2个队列
        SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {
                SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
                2
        };
        //pcm数据格式
        //SL_DATAFORMAT_PCM：数据格式为pcm格式
        //2：双声道
        //SL_SAMPLINGRATE_44_1：采样率为44100
        //SL_PCMSAMPLEFORMAT_FIXED_16：采样格式为16bit
        //SL_PCMSAMPLEFORMAT_FIXED_16：数据大小为16bit
        //SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT：左右声道（双声道）
        //SL_BYTEORDER_LITTLEENDIAN：小端模式
        SLDataFormat_PCM format_pcm = {SL_DATAFORMAT_PCM,
                                       2,
                                       SL_SAMPLINGRATE_44_1,
                                       SL_PCMSAMPLEFORMAT_FIXED_16,
                                       SL_PCMSAMPLEFORMAT_FIXED_16,
                                       SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT,
                                       SL_BYTEORDER_LITTLEENDIAN};

        //数据源 将上述配置信息放到这个数据源中
        SLDataSource audioSrc = {&loc_bufq, &format_pcm};

        //3.2 配置音轨（输出）
        //设置混音器
        SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
        SLDataSink audioSnk = {&loc_outmix, NULL};
        //需要的接口 操作队列的接口
        const SLInterfaceID ids[1] = {SL_IID_BUFFERQUEUE};
        const SLboolean req[1] = {SL_BOOLEAN_TRUE};
        //3.3 创建播放器
        result = (*engineInterface)->CreateAudioPlayer(engineInterface, &bqPlayerObject, &audioSrc,
                                                       &audioSnk, 1, ids, req);
        if (SL_RESULT_SUCCESS != result) {
            return;
        }
        //3.4 初始化播放器：SLObjectItf bqPlayerObject
        result = (*bqPlayerObject)->Realize(bqPlayerObject, SL_BOOLEAN_FALSE);
        if (SL_RESULT_SUCCESS != result) {
            return;
        }
        //3.5 获取播放器接口：SLPlayItf bqPlayerPlay
        result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_PLAY, &bqPlayerPlay);
        if (SL_RESULT_SUCCESS != result) {
            return;
        }
        /**
         * 4、设置播放回调函数
         */
        //4.1 获取播放器队列接口：SLAndroidSimpleBufferQueueItf bqPlayerBufferQueue
        (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_BUFFERQUEUE, &bqPlayerBufferQueue);

        //4.2 设置回调 void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context)
        (*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue, bqPlayerCallback, this);

        /**
         * 5、设置播放器状态为播放状态
         */
        (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);

        audioFrameQueue = new std::queue<AudioFrame *>;

        /**
         * 6、手动激活回调函数
         */
        bqPlayerCallback(bqPlayerBufferQueue, this);

//        LOGD("init audioFrameQueue:%p, renderer:%p", audioFrameQueue, this);
    }

    ~AudioRenderer()
    {
        (*bqPlayerObject)->Destroy(bqPlayerObject);
        (*engineObject)->Destroy(engineObject);
    }

    std::queue<AudioFrame *> *audioFrameQueue;

    void manualRender()
    {
        bqPlayerCallback(bqPlayerBufferQueue, this);
    };

private:
    //引擎
    SLObjectItf engineObject;
    //引擎接口
    SLEngineItf engineInterface;
    //混音器
    SLObjectItf outputMixObject;
    //播放器
    SLObjectItf bqPlayerObject;
    //播放器接口
    SLPlayItf bqPlayerPlay;
    //播放器队列接口
    SLAndroidSimpleBufferQueueItf bqPlayerBufferQueue;

    static void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context)
    {
        AudioRenderer *renderer = static_cast<AudioRenderer *>(context);
        if (renderer == nullptr) return;
        if (renderer->audioFrameQueue == nullptr) return;
        auto *audioFrameQueue = renderer->audioFrameQueue;
//        LOGD("bqPlayerCallback audioFrameQueue:%p, renderer:%p", audioFrameQueue, renderer);

        //播放存放在音频帧队列中的数据
        if (audioFrameQueue->size() <= 0) {
            return;
        }
        AudioFrame *audioFrame = audioFrameQueue->front();
        if (nullptr != audioFrame) {
            SLresult result = (*bq)->Enqueue(
                    bq,
                    audioFrame->buffer,
                    (SLuint32) audioFrame->buffer_size
            );
            if (result == SL_RESULT_SUCCESS) {
                audioFrameQueue->pop();
                delete audioFrame;
            }
        }
    }
};
