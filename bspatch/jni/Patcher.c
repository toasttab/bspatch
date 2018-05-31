#include "bspatch.h"
#include "Patcher.h"
#include <stdlib.h>
#include <android/log.h>

JNIEXPORT jint JNICALL Java_me_ele_patch_BsPatch_patch(JNIEnv* env,
        jobject othis, jstring argv1, jstring argv2, jstring argv3) {
    int result;
    const char *argv[4];

    ALOGI("Native patch");

    argv[0] = "bspatch";
    argv[1] = (*env)->GetStringUTFChars(env, argv1, 0);
    argv[2] = (*env)->GetStringUTFChars(env, argv2, 0);
    argv[3] = (*env)->GetStringUTFChars(env, argv3, 0);

    result = bspatch(4, argv);

    (*env)->ReleaseStringUTFChars(env, argv1, argv[1]);
    (*env)->ReleaseStringUTFChars(env, argv2, argv[2]);
    (*env)->ReleaseStringUTFChars(env, argv3, argv[3]);

    return result;
}
