#include <jni.h>
#include <stdint.h>

// ABI observed in the unmodified arm64 engine from NetEase 9.5.30.
// The original library is not patched; its setters and processing implement DSP.
JNIEXPORT jboolean JNICALL Java_io_github_currencortex_music_core_media_NeteasePcmBridge_initialize(
    JNIEnv *env,jobject self,jlong handle,jint rate,jint channels) {
    if(!handle||rate<8000||rate>192000||channels<1||channels>2)return JNI_FALSE;
    void *object=(void*)(uintptr_t)handle;void **vtable=*(void ***)object;
    int (*init)(void*,unsigned,unsigned,unsigned,unsigned)=(void*)vtable[0];
    // Signed interleaved 16-bit; 512 frames limits buffering during UI changes.
    return init(object,1,(unsigned)channels,(unsigned)rate,512*channels*2)?JNI_TRUE:JNI_FALSE;
}

static int read_output(void *object,void *output,unsigned capacity) {
    void **vtable=*(void ***)object;
    void (*read)(void*,void*,unsigned*,unsigned char*)=(void*)vtable[4];
    unsigned bytes=capacity;unsigned char eos=0;read(object,output,&bytes,&eos);
    return bytes<=capacity?(int)bytes:-1;
}

JNIEXPORT jint JNICALL Java_io_github_currencortex_music_core_media_NeteasePcmBridge_process(
    JNIEnv *env,jobject self,jlong handle,jobject input,jint offset,jint length,jobject output,jint capacity) {
    if(!handle||offset<0||length<=0||capacity<=0)return -1;
    void *source=(*env)->GetDirectBufferAddress(env,input),*dest=(*env)->GetDirectBufferAddress(env,output);
    jlong input_cap=(*env)->GetDirectBufferCapacity(env,input),output_cap=(*env)->GetDirectBufferCapacity(env,output);
    if(!source||!dest||(jlong)offset+length>input_cap||capacity>output_cap)return -1;
    void *object=(void*)(uintptr_t)handle;void **vtable=*(void ***)object;
    void (*push)(void*,void*,unsigned,int)=(void*)vtable[3];
    push(object,(char*)source+offset,(unsigned)length,-1);
    return read_output(object,dest,(unsigned)capacity);
}

JNIEXPORT jint JNICALL Java_io_github_currencortex_music_core_media_NeteasePcmBridge_drain(
    JNIEnv *env,jobject self,jlong handle,jobject output,jint capacity) {
    if(!handle||capacity<=0)return -1;
    void *dest=(*env)->GetDirectBufferAddress(env,output);
    if(!dest||capacity>(*env)->GetDirectBufferCapacity(env,output))return -1;
    void *object=(void*)(uintptr_t)handle;void **vtable=*(void ***)object;
    void (*push)(void*,void*,unsigned,int)=(void*)vtable[3];
    push(object,0,0,-1);
    return read_output(object,dest,(unsigned)capacity);
}
