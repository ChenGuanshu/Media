#include <jni.h>
#include <string>
#include "Utils.h"
#include "Entity.h"

jstring stringFromJNI2(JNIEnv *env, jobject instance) {
    LOGD("stringFromJNI2 %d, %s", 1, "oh!");
    std::string hello = "Hello from C++";
    Entity entity;
    auto str = entity.toString();
    return env->NewStringUTF(str.c_str());
}

static const JNINativeMethod nativeMethod[] = {
        // Java中的函数名
        {"stringFromJNI2",
                // 函数签名信息
         "()Ljava/lang/String;",
                // native的函数指针
         (void *) (stringFromJNI2)},
};

// 类库加载时自动调用
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reversed) {
    JNIEnv *env = NULL;
    // 初始化JNIEnv
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_FALSE;
    }
    // 找到需要动态动态注册的Jni类
    jclass jniClass = env->FindClass("com/guanshu/media/FfmpegPlayerActivity");
    if (nullptr == jniClass) {
        return JNI_FALSE;
    }
    // 动态注册
    env->RegisterNatives(jniClass, nativeMethod, sizeof(JNINativeMethod) / sizeof(nativeMethod));
    // 返回JNI使用的版本
    return JNI_VERSION_1_6;
}
