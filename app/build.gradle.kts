import java.util.Properties
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.baselineprofile")
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

// Reserve 100,000 codes per release; CI builds of that release use its trailing slots.
// A later stable release must increment the base versionCode below, as before.
val ciBuildNumber = providers.gradleProperty("ciBuildNumber").orNull?.toIntOrNull() ?: 0
require(ciBuildNumber in 0..99_999) { "CI build number must fit the reserved version-code range" }
require(!providers.gradleProperty("ciBuildNumber").isPresent || ciBuildNumber > 0) { "Invalid CI build number" }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

android {
    namespace = "io.github.currencortex.music"
    compileSdk = 37
    compileSdkMinor = 0
    ndkVersion = "30.0.16248370"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "4.1.2" } }

    defaultConfig {
        applicationId = "com.bileizhen.currentmusic"
        minSdk = 26
        // Only PR smoke builds opt into a single ABI; releases keep every supported ABI.
        if (providers.gradleProperty("ciPrArm64Only").orNull == "true") {
            ndk.abiFilters += "arm64-v8a"
        }
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_static" } }
        targetSdk = 36
        versionCode = 37
        versionName = "1.3.4"
        versionCode = requireNotNull(versionCode) * 100_000 + ciBuildNumber
        if (ciBuildNumber > 0) versionName = "$versionName-ci.$ciBuildNumber"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "NETEASE_ORIGINAL_EFFECTS", originalEffectsBundled.toString())
        buildConfigField("boolean", "UPDATE_DOWNLOAD_EXPERIENCE", "false")
        buildConfigField("String", "UPDATE_ANIMATION_PREVIEW_VERSION", "\"\"")
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    buildFeatures { compose = true; buildConfig = true }
    providers.gradleProperty("installedReleaseNotesDir").orNull?.let {
        sourceSets.getByName("main").assets.srcDir(it)
    }
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
        getByName("release") {
            signingConfig = keystoreProperties?.let { signingConfigs.getByName("release") }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Retain symbols as an external diagnostic artifact, not in every shipped ELF.
            ndk.debugSymbolLevel = "SYMBOL_TABLE"
        }
        create("performance") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            matchingFallbacks += listOf("release")
            manifestPlaceholders["appLabel"] = "CurrentMusic 性能测试"
        }
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
    // These exact, version-pinned vendor bytes are verified at configuration and import.
    // Stripping them would invalidate their ABI/hash contract. Our own JNI libraries can
    // be stripped normally; their symbol artifacts remain in the build output.
    packaging.jniLibs.keepDebugSymbols += setOf(
        "**/libncmusbaudio.so", "**/libneaudioeffects.so", "**/libc++_shared.so",
        "**/libMNN.so", "**/libMNN_Express.so", "**/libCore_MNN_JNI.so")
}

baselineProfile {
    // Profile generation is an explicit connected-device task, never a side effect
    // of assembling a release. The generated profile is shared with release.
    automaticGenerationDuringBuild = false
    mergeIntoMain = true
    saveInSrc = true
    dexLayoutOptimization = true
    filter { exclude("io.github.currencortex.music.benchmark.**") }
    variants {
        create("performance") { from(project(":baselineprofile")) }
    }
}

// AndroidX Baseline Profile 1.5 copies AndroidSourceDirectorySet.directories as strings.
// On AGP 8.13.2 that iterator calls Provider.toString(), producing a literal "provider(?)"
// source path in the synthetic variant. KSP then rejects '?' on Windows. Repair only that
// synthetic variant's static roots using AGP 8's resolved File API; KSP/Room still run and
// can register their generated roots normally after DSL finalization.
androidComponents.finalizeDsl { extension ->
    val original = extension.sourceSets.findByName("performance")
    val generated = extension.sourceSets.findByName("nonMinifiedPerformance")
    if (original != null && generated != null) {
        @Suppress("DEPRECATION")
        fun repairCopiedRoots(from: com.android.build.api.dsl.AndroidSourceDirectorySet,
                              to: com.android.build.api.dsl.AndroidSourceDirectorySet) {
            if (to.directories.any { it.startsWith("provider(") }) {
                val fromLegacy = from as com.android.build.gradle.api.AndroidSourceDirectorySet
                val toLegacy = to as com.android.build.gradle.api.AndroidSourceDirectorySet
                val staticRoots = toLegacy.srcDirs.filterNot { it.name.startsWith("provider(") } + fromLegacy.srcDirs
                to.setSrcDirs(staticRoots.distinct())
            }
        }
        repairCopiedRoots(original.java, generated.java)
        repairCopiedRoots(original.kotlin, generated.kotlin)
    }
}

dependencies {
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.javascriptengine:javascriptengine:1.1.1")
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
    implementation("com.materialkolor:material-color-utilities:4.1.1")

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
