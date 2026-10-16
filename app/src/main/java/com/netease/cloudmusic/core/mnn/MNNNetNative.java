package com.netease.cloudmusic.core.mnn;

import androidx.annotation.Keep;

/** Minimal exact original JNI declarations. Loading/ownership lives in OriginalMoodModel. */
@Keep
public final class MNNNetNative {
    private MNNNetNative() {}
    public static native long nativeCreateNetFromFile(String path);
    public static native long nativeCreateNetFromBuffer(byte[] model);
    public static native long nativeCreateSession(long net, int forwardType, int threads,
        String[] saveTensors, String[] outputTensors);
    public static native void nativeReleaseSession(long net, long session);
    public static native long nativeReleaseNet(long net);
    public static native MNNTensorInfo[] nativeGetSessionInputAll(long net, long session);
    public static native MNNTensorInfo[] nativeGetSessionOutputAll(long net, long session);
    public static native long nativeGetSessionInput(long net, long session, String name);
    public static native long nativeGetSessionOutput(long net, long session, String name);
    public static native void nativeSetInputFloatData(long net, long tensor, float[] values);
    public static native void nativeSetInputIntData(long net, long tensor, int[] values);
    public static native int nativeRunSession(long net, long session);
    public static native int nativeTensorGetData(long tensor, float[] values);
    public static native int[] nativeTensorGetDimensions(long tensor);
}
