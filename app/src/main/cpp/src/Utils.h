#pragma

#include <stdarg.h>
#include <android/log.h>

//void LOGD(const char *fmt)
//{
////    va_list args;
////    va_start(args, fmt);
////    __android_log_vprint(ANDROID_LOG_DEBUG, "QWER-native", fmt, args);
////    va_end(args);
////    __android_log_print(ANDROID_LOG_DEBUG, "QWER-native", "%s", fmt);
//}

#define LOG_TAG "QWER-native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
