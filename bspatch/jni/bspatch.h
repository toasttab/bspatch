#ifndef BSPATCH_H
#define BSPATCH_H

#include <jni.h>

#define LOG_TAG "bspatch"

#ifndef ALOGE
#define ALOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#endif

#ifndef ALOGI
#define ALOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))
#endif

extern int bspatch(int argc, const char **argv);
extern void jniThrowException(JNIEnv* env, const char* className, const char* msg);

#endif /* BSPATCH_H */
