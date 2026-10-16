package com.netease.cloudmusic.core.mnn;

import androidx.annotation.Keep;

/** ABI-only metadata carrier required by the unmodified original MNN JNI library. */
@Keep
public final class MNNTensorInfo {
    public final String name;
    public final int typeCode;
    public final int typeBits;
    public final int[] shape;

    public MNNTensorInfo(String name, int typeCode, int typeBits, int[] shape) {
        this.name = name;
        this.typeCode = typeCode;
        this.typeBits = typeBits;
        this.shape = shape;
    }
}
