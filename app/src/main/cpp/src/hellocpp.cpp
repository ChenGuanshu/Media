#include <jni.h>
#include <string>
#include "Utils.h"
#include "Entity.h"
#include "EglCore.h"

jstring stringFromJNI2(JNIEnv *env, jobject instance)
{
    LOGD("stringFromJNI2 %d, %s", 1, "oh!");

    std::string name = "test";
    Entity entity(name);
    // entity 栈上分配，在方法结束之后会销毁

    std::string str = entity.toString();

    Entity *entity1 = new Entity("test1");
    delete entity1;

    {
        auto entity2 = std::unique_ptr<Entity>(new Entity("test2"));
        // entity 堆上分配，在{}结束之后会销毁
    }

    std::weak_ptr<Entity> entityWeakPtr;
    std::shared_ptr<Entity> entitySharedPtr;
    {
        auto entity3 = std::make_shared<Entity>("test3");
        entityWeakPtr = entity3;
        entitySharedPtr = entity3;
        if (auto tempPtr = entityWeakPtr.lock()) {
            LOGD("access weak ptr %s", tempPtr->toString().c_str());
        }
    }
    LOGD("access shared ptr %s", entitySharedPtr->toString().c_str());

    if (auto tempPtr = entityWeakPtr.lock()) {
        LOGD("outside access weak ptr %s", tempPtr->toString().c_str());
    } else {
        LOGD("outside access weak ptr failed %s", ", oh no");
    }

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
JNIEXPORT jint

JNICALL JNI_OnLoad(JavaVM *vm, void *reversed)
{
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
