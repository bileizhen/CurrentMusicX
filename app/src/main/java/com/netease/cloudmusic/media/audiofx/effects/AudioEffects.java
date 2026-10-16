package com.netease.cloudmusic.media.audiofx.effects;

/** Reconstructed JNI declarations for local interoperability experiments only.
 * No vendor library, effect asset, license token, or authorization override is bundled.
 * Caller must explicitly load a compatible user-supplied native library first.
 */
@androidx.annotation.Keep
public class AudioEffects implements AutoCloseable {
    protected long mNativeInstance;
    public AudioEffects() { mNativeInstance = create(); }
    public synchronized long getNativeInstance() { return mNativeInstance; }
    @Override public synchronized void close() {
        if (mNativeInstance != 0) { finalizer(mNativeInstance); mNativeInstance = 0; }
    }
    protected native long create();
    protected native void dumpParams(long p0);
    protected native void finalizer(long p0);
    protected native java.lang.String[] getCustomEffectChainOrder(long p0);
    protected native java.lang.String[] getEffectChainOrder(long p0);
    protected native void resetEffectChainOrder(long p0);
    protected native void resetParams(long p0);
    protected native void setALCON(long p0, boolean p1);
    protected native void setALCParams(long p0, int p1, float p2, float p3, float p4, float p5, float p6, float p7, int p8, float p9, float[] p10);
    protected native void setBassTrebleExtraParams(long p0, float p1, float p2, float p3, float p4);
    protected native void setBassTrebleON(long p0, boolean p1);
    protected native void setBassTrebleParams(long p0, float p1, float p2);
    protected native void setBypassMode(long p0, boolean p1);
    protected native void setChannelDelayON(long p0, boolean p1);
    protected native void setChannelDelayParams(long p0, float[] p1, float[] p2);
    protected native void setChorusON(long p0, boolean p1);
    protected native void setChorusParams(long p0, float p1, float p2, float p3, float p4, int p5);
    protected native void setCompressorON(long p0, boolean p1);
    protected native void setCompressorParams(long p0, float p1, float p2, float p3, float p4, float p5, float p6, float p7);
    protected native boolean setCustomEffectChainOrder(long p0, java.lang.String[] p1);
    protected native void setDelayON(long p0, boolean p1);
    protected native void setDelayParams(long p0, float p1, float p2, float p3, float p4);
    protected native void setDynamicEQBandParams(long p0, int p1, float p2, float p3, int p4, float p5, float p6, float p7, float p8, float p9, float p10, float p11, float p12, boolean p13);
    protected native void setDynamicEQDryWetMix(long p0, float p1);
    protected native void setDynamicEQON(long p0, boolean p1);
    protected native void setDynamicEQOutputGain(long p0, float p1);
    protected native void setEarFingerAudioON(long p0, boolean p1);
    protected native void setEarFingerParams(long p0, java.lang.String p1);
    protected native boolean setEffectChainOrder(long p0, java.lang.String[] p1);
    protected native void setExParametricEQFilterParams(long p0, int p1, int p2, float p3, float p4, float p5, boolean p6);
    protected native void setExParametricEQON(long p0, boolean p1);
    protected native void setExParametricEQParams(long p0, float p1);
    protected native void setExParametricEQRangeParams(long p0, int p1, int p2, float p3, float p4, int p5, int p6, boolean p7);
    protected native void setFIRImpulse(long p0, byte[] p1);
    protected native void setFIRON(long p0, boolean p1);
    protected native void setFlangerON(long p0, boolean p1);
    protected native void setFlangerParams(long p0, float p1, float p2, float p3, float p4, float p5);
    protected native void setGraphEQGains(long p0, float[] p1);
    protected native void setGraphEQON(long p0, boolean p1);
    protected native void setHeadphoneImpulse(long p0, byte[] p1);
    protected native void setHeadphoneON(long p0, boolean p1);
    protected native void setHearAudioGram(long p0, float[] p1);
    protected native void setHearAudioON(long p0, boolean p1);
    protected native void setHearDefaultLoss(long p0, float[] p1);
    protected native void setLimiterON(long p0, boolean p1);
    protected native void setLimiterParams(long p0, float p1, float p2, float p3, float p4, boolean p5, float p6);
    protected native void setLoudnessON(long p0, boolean p1);
    protected native void setLoudnessParams(long p0, float p1, float p2);
    protected native void setLoudnessPlaybackRef(long p0, float p1);
    protected native void setMBStereoFieldON(long p0, boolean p1);
    protected native void setMBStereoFieldParams(long p0, int p1, float[] p2, float[] p3, float[] p4, float p5, boolean p6);
    protected native void setMultiBandCompressorBandParams(long p0, int p1, float p2, float p3, float p4, float p5, float p6, float p7, float p8);
    protected native void setMultiBandCompressorCrossoverFrequency(long p0, int p1, float p2);
    protected native void setMultiBandCompressorCrossoverParams(long p0, float[] p1);
    protected native void setMultiBandCompressorGlobalParams(long p0, float p1, boolean p2, boolean p3, boolean p4, int p5, float p6);
    protected native void setMultiBandCompressorON(long p0, boolean p1);
    protected native void setNoiseGenON(long p0, boolean p1);
    protected native void setNoiseGenParams(long p0, int p1, int p2, float p3, float p4, float p5, float p6, float p7, float p8);
    protected native void setNoiseGenPeqFilterParams(long p0, int p1, int p2, float p3, float p4, float p5, boolean p6);
    protected native void setNoiseGenPeqOnOff(long p0, boolean p1);
    protected native void setParametricEQFilterParams(long p0, int p1, int p2, float p3, float p4, float p5, boolean p6);
    protected native void setParametricEQON(long p0, boolean p1);
    protected native void setParametricEQParams(long p0, float p1);
    protected native void setPitchChangerON(long p0, boolean p1);
    protected native void setPitchChangerParams(long p0, double p1);
    protected native void setPostCompressorON(long p0, boolean p1);
    protected native void setPostCompressorParams(long p0, float p1, float p2, float p3, float p4, float p5, float p6, float p7);
    protected native void setReverbERParams(long p0, boolean p1, int p2, float p3, float p4);
    protected native void setReverbFilterParams(long p0, int p1, int p2, int p3, float p4, float p5, float p6);
    protected native void setReverbInputLevelParams(long p0, float p1, float p2);
    protected native void setReverbLevelParams(long p0, float p1, float p2, float p3, float p4);
    protected native void setReverbON(long p0, boolean p1);
    protected native void setReverbOutputLevelParams(long p0, float p1, float p2, float p3);
    protected native void setReverbParams(long p0, float p1, float p2, float p3, float p4, float p5, int p6, float p7, float p8);
    protected native void setReverbTCEnable(long p0, boolean p1);
    protected native void setRobotizationON(long p0, boolean p1);
    protected native void setRobotizationParams(long p0, int p1);
    protected native void setRotate3DON(long p0, boolean p1);
    protected native void setRotate3DParams(long p0, float p1);
    protected native void setSpatialAudioON(long p0, boolean p1);
    protected native void setSpatialAudioParams(long p0, float p1, float p2, float p3, float p4, float p5, float p6, float p7, float p8, float p9, float p10, float p11, float p12, boolean p13);
    protected native void setSpeakerSpatialON(long p0, boolean p1);
    protected native void setSpeakerSpatialParams(long p0, int p1, int p2, int p3, int p4, float p5, float p6, float p7, float p8, float p9, float p10, float p11, float p12, float p13, float p14, float p15);
    protected native void setStereoEnhancerON(long p0, boolean p1);
    protected native void setStereoEnhancerParams(long p0, float p1, float p2, boolean p3, int p4);
    protected native void setTremoloON(long p0, boolean p1);
    protected native void setTremoloParams(long p0, double p1, double p2);
    protected native void setVibratoON(long p0, boolean p1);
    protected native void setVibratoParams(long p0, double p1, double p2);
    public synchronized void dumpParams() {
        if (mNativeInstance == 0) { return; }
        dumpParams(mNativeInstance);
    }
    public synchronized java.lang.String[] getCustomEffectChainOrder() {
        if (mNativeInstance == 0) { return null; }
        return getCustomEffectChainOrder(mNativeInstance);
    }
    public synchronized java.lang.String[] getEffectChainOrder() {
        if (mNativeInstance == 0) { return null; }
        return getEffectChainOrder(mNativeInstance);
    }
    public synchronized void resetEffectChainOrder() {
        if (mNativeInstance == 0) { return; }
        resetEffectChainOrder(mNativeInstance);
    }
    public synchronized void resetParams() {
        if (mNativeInstance == 0) { return; }
        resetParams(mNativeInstance);
    }
    public synchronized void setALCON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setALCON(mNativeInstance, p0);
    }
    public synchronized void setALCParams(int p0, float p1, float p2, float p3, float p4, float p5, float p6, int p7, float p8, float[] p9) {
        if (mNativeInstance == 0) { return; }
        setALCParams(mNativeInstance, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }
    public synchronized void setBassTrebleExtraParams(float p0, float p1, float p2, float p3) {
        if (mNativeInstance == 0) { return; }
        setBassTrebleExtraParams(mNativeInstance, p0, p1, p2, p3);
    }
    public synchronized void setBassTrebleON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setBassTrebleON(mNativeInstance, p0);
    }
    public synchronized void setBassTrebleParams(float p0, float p1) {
        if (mNativeInstance == 0) { return; }
        setBassTrebleParams(mNativeInstance, p0, p1);
    }
    public synchronized void setChannelDelayON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setChannelDelayON(mNativeInstance, p0);
    }
    public synchronized void setChannelDelayParams(float[] p0, float[] p1) {
        if (mNativeInstance == 0) { return; }
        setChannelDelayParams(mNativeInstance, p0, p1);
    }
    public synchronized void setChorusON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setChorusON(mNativeInstance, p0);
    }
    public synchronized void setChorusParams(float p0, float p1, float p2, float p3, int p4) {
        if (mNativeInstance == 0) { return; }
        setChorusParams(mNativeInstance, p0, p1, p2, p3, p4);
    }
    public synchronized void setCompressorON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setCompressorON(mNativeInstance, p0);
    }
    public synchronized void setCompressorParams(float p0, float p1, float p2, float p3, float p4, float p5, float p6) {
        if (mNativeInstance == 0) { return; }
        setCompressorParams(mNativeInstance, p0, p1, p2, p3, p4, p5, p6);
    }
    public synchronized boolean setCustomEffectChainOrder(java.lang.String[] p0) {
        if (mNativeInstance == 0) { return false; }
        return setCustomEffectChainOrder(mNativeInstance, p0);
    }
    public synchronized void setDelayON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setDelayON(mNativeInstance, p0);
    }
    public synchronized void setDelayParams(float p0, float p1, float p2, float p3) {
        if (mNativeInstance == 0) { return; }
        setDelayParams(mNativeInstance, p0, p1, p2, p3);
    }
    public synchronized void setDynamicEQBandParams(int p0, float p1, float p2, int p3, float p4, float p5, float p6, float p7, float p8, float p9, float p10, float p11, boolean p12) {
        if (mNativeInstance == 0) { return; }
        setDynamicEQBandParams(mNativeInstance, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12);
    }
    public synchronized void setDynamicEQDryWetMix(float p0) {
        if (mNativeInstance == 0) { return; }
        setDynamicEQDryWetMix(mNativeInstance, p0);
    }
    public synchronized void setDynamicEQON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setDynamicEQON(mNativeInstance, p0);
    }
    public synchronized void setDynamicEQOutputGain(float p0) {
        if (mNativeInstance == 0) { return; }
        setDynamicEQOutputGain(mNativeInstance, p0);
    }
    public synchronized void setEarFingerAudioON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setEarFingerAudioON(mNativeInstance, p0);
    }
    public synchronized void setEarFingerParams(java.lang.String p0) {
        if (mNativeInstance == 0) { return; }
        setEarFingerParams(mNativeInstance, p0);
    }
    public synchronized boolean setEffectChainOrder(java.lang.String[] p0) {
        if (mNativeInstance == 0) { return false; }
        return setEffectChainOrder(mNativeInstance, p0);
    }
    public synchronized void setExParametricEQFilterParams(int p0, int p1, float p2, float p3, float p4, boolean p5) {
        if (mNativeInstance == 0) { return; }
        setExParametricEQFilterParams(mNativeInstance, p0, p1, p2, p3, p4, p5);
    }
    public synchronized void setExParametricEQON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setExParametricEQON(mNativeInstance, p0);
    }
    public synchronized void setExParametricEQParams(float p0) {
        if (mNativeInstance == 0) { return; }
        setExParametricEQParams(mNativeInstance, p0);
    }
    public synchronized void setExParametricEQRangeParams(int p0, int p1, float p2, float p3, int p4, int p5, boolean p6) {
        if (mNativeInstance == 0) { return; }
        setExParametricEQRangeParams(mNativeInstance, p0, p1, p2, p3, p4, p5, p6);
    }
    public synchronized void setFIRON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setFIRON(mNativeInstance, p0);
    }
    public synchronized void setFIRParams(byte[] p0) {
        if (mNativeInstance == 0) { return; }
        setFIRImpulse(mNativeInstance, p0);
    }
    public synchronized void setFlangerON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setFlangerON(mNativeInstance, p0);
    }
    public synchronized void setFlangerParams(float p0, float p1, float p2, float p3, float p4) {
        if (mNativeInstance == 0) { return; }
        setFlangerParams(mNativeInstance, p0, p1, p2, p3, p4);
    }
    public synchronized void setGraphEQGains(float[] p0) {
        if (mNativeInstance == 0) { return; }
        setGraphEQGains(mNativeInstance, p0);
    }
    public synchronized void setGraphEQON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setGraphEQON(mNativeInstance, p0);
    }
    public synchronized void setHeadphoneON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setHeadphoneON(mNativeInstance, p0);
    }
    public synchronized void setHeadphoneParams(byte[] p0) {
        if (mNativeInstance == 0) { return; }
        setHeadphoneImpulse(mNativeInstance, p0);
    }
    public synchronized void setHearAudioGram(float[] p0) {
        if (mNativeInstance == 0) { return; }
        setHearAudioGram(mNativeInstance, p0);
    }
    public synchronized void setHearAudioON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setHearAudioON(mNativeInstance, p0);
    }
    public synchronized void setLimiterON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setLimiterON(mNativeInstance, p0);
    }
    public synchronized void setLimiterParams(float p0, float p1, float p2, float p3, boolean p4, float p5) {
        if (mNativeInstance == 0) { return; }
        setLimiterParams(mNativeInstance, p0, p1, p2, p3, p4, p5);
    }
    public synchronized void setLoudnessON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setLoudnessON(mNativeInstance, p0);
    }
    public synchronized void setLoudnessParams(float p0, float p1) {
        if (mNativeInstance == 0) { return; }
        setLoudnessParams(mNativeInstance, p0, p1);
    }
    public synchronized void setLoudnessPlaybackRef(float p0) {
        if (mNativeInstance == 0) { return; }
        setLoudnessPlaybackRef(mNativeInstance, p0);
    }
    public synchronized void setMBStereoFieldON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setMBStereoFieldON(mNativeInstance, p0);
    }
    public synchronized void setMBStereoFieldParams(int p0, float[] p1, float[] p2, float[] p3, float p4, boolean p5) {
        if (mNativeInstance == 0) { return; }
        setMBStereoFieldParams(mNativeInstance, p0, p1, p2, p3, p4, p5);
    }
    public synchronized void setMultiBandCompressorBandParams(int p0, float p1, float p2, float p3, float p4, float p5, float p6, float p7) {
        if (mNativeInstance == 0) { return; }
        setMultiBandCompressorBandParams(mNativeInstance, p0, p1, p2, p3, p4, p5, p6, p7);
    }
    public synchronized void setMultiBandCompressorCrossoverFrequency(int p0, float p1) {
        if (mNativeInstance == 0) { return; }
        setMultiBandCompressorCrossoverFrequency(mNativeInstance, p0, p1);
    }
    public synchronized void setMultiBandCompressorCrossoverParams(float[] p0) {
        if (mNativeInstance == 0) { return; }
        setMultiBandCompressorCrossoverParams(mNativeInstance, p0);
    }
    public synchronized void setMultiBandCompressorGlobalParams(float p0, boolean p1, boolean p2, boolean p3, int p4, float p5) {
        if (mNativeInstance == 0) { return; }
        setMultiBandCompressorGlobalParams(mNativeInstance, p0, p1, p2, p3, p4, p5);
    }
    public synchronized void setMultiBandCompressorON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setMultiBandCompressorON(mNativeInstance, p0);
    }
    public synchronized void setNoiseGenON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setNoiseGenON(mNativeInstance, p0);
    }
    public synchronized void setNoiseGenParams(int p0, int p1, float p2, float p3, float p4, float p5, float p6, float p7) {
        if (mNativeInstance == 0) { return; }
        setNoiseGenParams(mNativeInstance, p0, p1, p2, p3, p4, p5, p6, p7);
    }
    public synchronized void setNoiseGenPeqFilterParams(int p0, int p1, float p2, float p3, float p4, boolean p5) {
        if (mNativeInstance == 0) { return; }
        setNoiseGenPeqFilterParams(mNativeInstance, p0, p1, p2, p3, p4, p5);
    }
    public synchronized void setNoiseGenPeqOnOff(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setNoiseGenPeqOnOff(mNativeInstance, p0);
    }
    public synchronized void setON(boolean enabled) {
        if (mNativeInstance != 0) setBypassMode(mNativeInstance, !enabled);
    }
    public synchronized void setParametricEQFilterParams(int p0, int p1, float p2, float p3, float p4, boolean p5) {
        if (mNativeInstance == 0) { return; }
        setParametricEQFilterParams(mNativeInstance, p0, p1, p2, p3, p4, p5);
    }
    public synchronized void setParametricEQON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setParametricEQON(mNativeInstance, p0);
    }
    public synchronized void setParametricEQParams(float p0) {
        if (mNativeInstance == 0) { return; }
        setParametricEQParams(mNativeInstance, p0);
    }
    public synchronized void setPitchChangerON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setPitchChangerON(mNativeInstance, p0);
    }
    public synchronized void setPitchChangerParams(double p0) {
        if (mNativeInstance == 0) { return; }
        setPitchChangerParams(mNativeInstance, p0);
    }
    public synchronized void setPostCompressorON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setPostCompressorON(mNativeInstance, p0);
    }
    public synchronized void setPostCompressorParams(float p0, float p1, float p2, float p3, float p4, float p5, float p6) {
        if (mNativeInstance == 0) { return; }
        setPostCompressorParams(mNativeInstance, p0, p1, p2, p3, p4, p5, p6);
    }
    public synchronized void setReverbERParams(boolean p0, int p1, float p2, float p3) {
        if (mNativeInstance == 0) { return; }
        setReverbERParams(mNativeInstance, p0, p1, p2, p3);
    }
    public synchronized void setReverbFilterParams(int p0, int p1, int p2, float p3, float p4, float p5) {
        if (mNativeInstance == 0) { return; }
        setReverbFilterParams(mNativeInstance, p0, p1, p2, p3, p4, p5);
    }
    public synchronized void setReverbInputLevelParams(float p0, float p1) {
        if (mNativeInstance == 0) { return; }
        setReverbInputLevelParams(mNativeInstance, p0, p1);
    }
    public synchronized void setReverbLevelParams(float p0, float p1, float p2, float p3) {
        if (mNativeInstance == 0) { return; }
        setReverbLevelParams(mNativeInstance, p0, p1, p2, p3);
    }
    public synchronized void setReverbON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setReverbON(mNativeInstance, p0);
    }
    public synchronized void setReverbOutputLevelParams(float p0, float p1, float p2) {
        if (mNativeInstance == 0) { return; }
        setReverbOutputLevelParams(mNativeInstance, p0, p1, p2);
    }
    public synchronized void setReverbParams(float p0, float p1, float p2, float p3, float p4, int p5, float p6, float p7) {
        if (mNativeInstance == 0) { return; }
        setReverbParams(mNativeInstance, p0, p1, p2, p3, p4, p5, p6, p7);
    }
    public synchronized void setReverbTCEnable(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setReverbTCEnable(mNativeInstance, p0);
    }
    public synchronized void setRobotizationON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setRobotizationON(mNativeInstance, p0);
    }
    public synchronized void setRobotizationParams(int p0) {
        if (mNativeInstance == 0) { return; }
        setRobotizationParams(mNativeInstance, p0);
    }
    public synchronized void setRotate3DON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setRotate3DON(mNativeInstance, p0);
    }
    public synchronized void setRotate3DParams(float p0) {
        if (mNativeInstance == 0) { return; }
        setRotate3DParams(mNativeInstance, p0);
    }
    public synchronized void setSpatialAudioON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setSpatialAudioON(mNativeInstance, p0);
    }
    public synchronized void setSpatialAudioParams(float p0, float p1, float p2, float p3, float p4, float p5, float p6, float p7, float p8, float p9, float p10, float p11, boolean p12) {
        if (mNativeInstance == 0) { return; }
        setSpatialAudioParams(mNativeInstance, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12);
    }
    public synchronized void setSpeakerSpatialON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setSpeakerSpatialON(mNativeInstance, p0);
    }
    public synchronized void setSpeakerSpatialParams(int p0, int p1, int p2, int p3, float p4, float p5, float p6, float p7, float p8, float p9, float p10, float p11, float p12, float p13, float p14) {
        if (mNativeInstance == 0) { return; }
        setSpeakerSpatialParams(mNativeInstance, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14);
    }
    public synchronized void setStereoEnhancerON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setStereoEnhancerON(mNativeInstance, p0);
    }
    public synchronized void setStereoEnhancerParams(float p0, float p1, boolean p2, int p3) {
        if (mNativeInstance == 0) { return; }
        setStereoEnhancerParams(mNativeInstance, p0, p1, p2, p3);
    }
    public synchronized void setTremoloON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setTremoloON(mNativeInstance, p0);
    }
    public synchronized void setTremoloParams(double p0, double p1) {
        if (mNativeInstance == 0) { return; }
        setTremoloParams(mNativeInstance, p0, p1);
    }
    public synchronized void setVibratoON(boolean p0) {
        if (mNativeInstance == 0) { return; }
        setVibratoON(mNativeInstance, p0);
    }
    public synchronized void setVibratoParams(double p0, double p1) {
        if (mNativeInstance == 0) { return; }
        setVibratoParams(mNativeInstance, p0, p1);
    }
}
