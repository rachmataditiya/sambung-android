/*
 * Added for Sambung, November 2026.
 *
 * The bridge between Kotlin and Codec2 in mode 700C: 40 ms frames of 320 samples at 8 kHz, each packed into 4 bytes.
 * Both functions work on whole frames; the Kotlin side pads or trims to that.
 */
#include <jni.h>
#include <stdlib.h>

#include "codec2.h"

#define SAMPLES_PER_FRAME 320
#define BYTES_PER_FRAME 4

JNIEXPORT jbyteArray JNICALL
Java_org_meshtastic_core_codec2_SambungCodec2_nativeEncode(JNIEnv *env, jobject thiz, jshortArray samples) {
    jsize frames = (*env)->GetArrayLength(env, samples) / SAMPLES_PER_FRAME;
    struct CODEC2 *codec = codec2_create(CODEC2_MODE_700C);
    unsigned char *bytes = malloc((size_t)frames * BYTES_PER_FRAME + 1);
    jshort *speech = (*env)->GetShortArrayElements(env, samples, NULL);
    jbyteArray result = NULL;

    if (codec != NULL && bytes != NULL && speech != NULL) {
        for (jsize frame = 0; frame < frames; frame++) {
            codec2_encode(codec, bytes + frame * BYTES_PER_FRAME, speech + frame * SAMPLES_PER_FRAME);
        }
        result = (*env)->NewByteArray(env, frames * BYTES_PER_FRAME);
        if (result != NULL) {
            (*env)->SetByteArrayRegion(env, result, 0, frames * BYTES_PER_FRAME, (const jbyte *)bytes);
        }
    }

    if (speech != NULL) (*env)->ReleaseShortArrayElements(env, samples, speech, JNI_ABORT);
    free(bytes);
    if (codec != NULL) codec2_destroy(codec);
    return result;
}

JNIEXPORT jshortArray JNICALL
Java_org_meshtastic_core_codec2_SambungCodec2_nativeDecode(JNIEnv *env, jobject thiz, jbyteArray clip) {
    jsize frames = (*env)->GetArrayLength(env, clip) / BYTES_PER_FRAME;
    struct CODEC2 *codec = codec2_create(CODEC2_MODE_700C);
    short *speech = malloc(((size_t)frames * SAMPLES_PER_FRAME + 1) * sizeof(short));
    jbyte *bytes = (*env)->GetByteArrayElements(env, clip, NULL);
    jshortArray result = NULL;

    if (codec != NULL && speech != NULL && bytes != NULL) {
        for (jsize frame = 0; frame < frames; frame++) {
            codec2_decode(codec, speech + frame * SAMPLES_PER_FRAME,
                          (const unsigned char *)bytes + frame * BYTES_PER_FRAME);
        }
        result = (*env)->NewShortArray(env, frames * SAMPLES_PER_FRAME);
        if (result != NULL) {
            (*env)->SetShortArrayRegion(env, result, 0, frames * SAMPLES_PER_FRAME, speech);
        }
    }

    if (bytes != NULL) (*env)->ReleaseByteArrayElements(env, clip, bytes, JNI_ABORT);
    free(speech);
    if (codec != NULL) codec2_destroy(codec);
    return result;
}
