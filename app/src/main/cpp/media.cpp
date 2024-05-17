#include <jni.h>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_guanshu_media_FfmpegPlayerActivity_stringFromNative(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("From JNI");
}