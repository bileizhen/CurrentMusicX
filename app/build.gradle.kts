import java.util.Properties
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// keystore.properties (gitignored): storeFile / storePassword / keyAlias / keyPassword.
// Absent file keeps assembleRelease unsigned, e.g. on CI without signing secrets.
val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }
}

// Ordinary builds include the verified arm64 engine, JNI bridge and MNN runtime.
// The opt-out is retained only for import-path interoperability tests.
val originalEffectsBundled = providers.gradleProperty("neteaseOriginalEffects").orNull != "false"
val updateDownloadExperience = providers.gradleProperty("updateDownloadExperience").orNull == "true"
val updateAnimationPreviewVersion = providers.gradleProperty("updateAnimationPreviewVersion").orNull.orEmpty()
require(updateAnimationPreviewVersion.isEmpty() ||
    (providers.gradleProperty("isolatedDebug").orNull == "true" &&
        updateAnimationPreviewVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))) {
    "Version animation preview requires isolatedDebug and a numeric version"
}
require(!updateDownloadExperience || providers.gradleProperty("isolatedDebug").orNull == "true") {
    "Download experience requires the isolated debug package"
}
val originalEffectsLibs = file("src/main/jniLibs/arm64-v8a")

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// The installed version can show its own notes even when GitHub has no newer release.
val bundleReleaseHistory by tasks.registering(Copy::class) {
    from(rootProject.file("CHANGELOG.md"))
    into(layout.buildDirectory.dir("generated/releaseHistory"))
    rename { "release-history.md" }
}

android {
    namespace = "io.github.currencortex.music"
    compileSdk = 37
    compileSdkMinor = 0
    ndkVersion = "30.0.16248370"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "4.1.2" } }

    defaultConfig {
        applicationId = "com.bileizhen.currentmusic"
        minSdk = 26
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_static" } }
        targetSdk = 36
        versionCode = 30
        versionName = "1.2.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "NETEASE_ORIGINAL_EFFECTS", originalEffectsBundled.toString())
        buildConfigField("boolean", "UPDATE_DOWNLOAD_EXPERIENCE", "false")
        buildConfigField("String", "UPDATE_ANIMATION_PREVIEW_VERSION", "\"\"")
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("main").assets.srcDir(bundleReleaseHistory.map { it.destinationDir })
    sourceSets.getByName("main").jniLibs.setSrcDirs(if (originalEffectsBundled) listOf(file("src/main/jniLibs")) else emptyList<File>())
    if (originalEffectsBundled) {
        val pinned = mapOf(
            "libncmusbaudio.so" to "150daa62df3b2f5a9b9bb5d54a3108e90495e50853c4b5ac7df3f63137f70a7a",
            "libneaudioeffects.so" to "3f8e2eec659080cb8e90bbfc672d327a180e4e0b6c051818e38625c5f01fcbd1",
            "libc++_shared.so" to "3e6d529147ffd66bd31fab85c8902b526abd1c06e43554ce64b97519e255e4b3",
            "libMNN.so" to "be7ad29454cfeecfb30399e32c3f600f963e69c7dd914f0001021fc78435249d",
            "libMNN_Express.so" to "e095c40a22d3dbb83894c74b3735db857e5137079109cab25797864921d4de57",
            "libCore_MNN_JNI.so" to "fec5363885343072c9aaf395a8560b217def8600404fa2ab9c940509215e0bef")
        pinned.forEach { (name, expected) ->
            val binary = originalEffectsLibs.resolve(name)
            check(binary.isFile && MessageDigest.getInstance("SHA-256").digest(binary.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 255) } == expected) {
                "Bundled NetEase library failed verification: $name"
            }
        }
    }
    if (providers.gradleProperty("neteaseOriginalTestAssets").orNull == "true") {
        sourceSets.getByName("androidTest").assets.srcDir(rootProject.file(".verification/sound-effects/port/test-assets"))
    }
    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            buildConfigField("String", "UPDATE_ANIMATION_PREVIEW_VERSION", "\"$updateAnimationPreviewVersion\"")
            // Keep instrumentation separate from the user's signed release installation.
            if (providers.gradleProperty("isolatedDebug").orNull == "true") applicationIdSuffix = ".verification"
            if (updateDownloadExperience) {
                versionNameSuffix = "-alpha.0"
                buildConfigField("boolean", "UPDATE_DOWNLOAD_EXPERIENCE", "true")
                manifestPlaceholders["appLabel"] = "CurrentMusic 下载体验"
            }
        }
        getByName("release") { signingConfig = keystoreProperties?.let { signingConfigs.getByName("release") } }
    }
    compileOptions {
        encoding = "UTF-8"
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    packaging { resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/AL2.0", "META-INF/LGPL2.1") }
    // Extract version-bound vendor libraries to the app's native library directory.
    packaging.jniLibs.useLegacyPackaging = true
    packaging.jniLibs.keepDebugSymbols += "**/*.so"
}

tasks.named("preBuild") { dependsOn(bundleReleaseHistory) }

dependencies {
    implementation("org.mozilla:rhino:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("io.coil-kt.coil3:coil-gif:3.3.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.material:material-icons-core:1.7.8")

    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-squircle-android:0.9.3")

    implementation("androidx.navigation3:navigation3-runtime:1.1.4")
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.work:work-testing:2.12.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.11.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.11.2")
}
