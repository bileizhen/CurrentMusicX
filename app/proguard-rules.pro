# CurrentMusic's JNI exports encode these class/method names. Descriptor classes must
# retain their identities too. This also documents the default Android native rule.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# The unmodified NetEase binaries register complete native method tables and look up
# the metadata carrier's exact constructor/field names. Do not prune unused methods
# from these ABI declarations, even when only a subset is called by Kotlin.
-keep class com.netease.cloudmusic.media.audiofx.effects.AudioEffects { *; }
-keep class com.netease.cloudmusic.core.mnn.MNNNetNative { *; }
-keep class com.netease.cloudmusic.core.mnn.MNNTensorInfo { *; }

# WebView calls these named Java members from bundled page/recognition JavaScript.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Rhino 1.8.1 has no consumer rules. Native built-ins, XML adapters and optimizer
# classes use reflective lookups/annotated method discovery. Keep this bounded
# interpreter island; do not keep the application's DTOs or all dependency packages.
-keep class org.mozilla.javascript.** { *; }
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# Rhino 1.8.1 also contains its desktop JVM bytecode compiler. Every application
# ContextFactory (OriginalMoodCodec, OriginalAiMasterCodec, FoliaLyricsMatchPolicy)
# sets optimizationLevel=-1 before evaluating safe JS; Context.createCompiler()
# selects Interpreter, so the JDK Dynalink optimizer bootstrap is never executed.
# Keep this exact missing-type list bounded: do not suppress other JNI/runtime errors.
-dontwarn jdk.dynalink.CallSiteDescriptor
-dontwarn jdk.dynalink.DynamicLinker
-dontwarn jdk.dynalink.DynamicLinkerFactory
-dontwarn jdk.dynalink.NamedOperation
-dontwarn jdk.dynalink.Namespace
-dontwarn jdk.dynalink.NamespaceOperation
-dontwarn jdk.dynalink.Operation
-dontwarn jdk.dynalink.RelinkableCallSite
-dontwarn jdk.dynalink.StandardNamespace
-dontwarn jdk.dynalink.StandardOperation
-dontwarn jdk.dynalink.linker.GuardedInvocation
-dontwarn jdk.dynalink.linker.GuardingDynamicLinker
-dontwarn jdk.dynalink.linker.LinkRequest
-dontwarn jdk.dynalink.linker.LinkerServices
-dontwarn jdk.dynalink.linker.TypeBasedGuardingDynamicLinker
-dontwarn jdk.dynalink.linker.support.CompositeTypeBasedGuardingDynamicLinker
-dontwarn jdk.dynalink.linker.support.Guards
-dontwarn jdk.dynalink.support.ChainedCallSite

# JavaToJSONConverters.BEAN is an optional desktop JavaBean converter. Context's
# default is STRING, and our sandboxes pass parsed JS JSON, never wrapped beans or
# setJavaToJSONConverter(BEAN). Suppress only that converter's absent desktop APIs.
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

# Jaudiotagger 3.0.1 selects frame bodies using "FrameBody" + the ID3 identifier,
# then uses public constructors for decoding and copying (AbstractID3v2Frame/ID3Tags).
-keep class org.jaudiotagger.tag.id3.framebody.** {
    public <init>(...);
}
-keepclassmembers class org.jaudiotagger.** {
    public <init>(...);
}

# SongTagWriter sets TagOptionSingleton.isAndroid=true and uses AndroidArtwork's
# bytes/dimensions. StandardArtwork.getImage() is the unused desktop image decoder;
# no app path calls it, and no java.awt/ImageIO implementation is bundled on Android.
-dontwarn java.awt.image.BufferedImage
-dontwarn javax.imageio.ImageIO
-dontwarn javax.imageio.stream.ImageInputStream

# Room and kotlinx.serialization ship their own consumer rules for generated
# implementations/serializers. Retain those rules rather than disabling shrinking
# for the entire database/model package. SerialName/wire field strings are preserved
# by generated serializers, independently of obfuscated Kotlin symbol names.
