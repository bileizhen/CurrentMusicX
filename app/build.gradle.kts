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
        minSdk = 33
        // Only PR smoke builds opt into a single ABI; releases keep every supported ABI.
        if (providers.gradleProperty("ciPrArm64Only").orNull == "true") {
            ndk.abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_static"
                // The Android NDK toolchain supports ccache through ANDROID_CCACHE.
                // CI-only, so local builds and release signing are unchanged.
                if (providers.gradleProperty("ciNativeCcache").orNull == "true") {
                    arguments += "-DANDROID_CCACHE=ccache"
                }
            }
        }
        targetSdk = 36
        versionCode = 39
        versionName = "1.3.6"
        versionCode = requireNotNull(versionCode) * 100_000 + ciBuildNumber
        if (ciBuildNumber > 0) versionName = "$versionName-ci.$ciBuildNumber"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "UPDATE_DOWNLOAD_EXPERIENCE", "false")
        buildConfigField("String", "UPDATE_ANIMATION_PREVIEW_VERSION", "\"\"")
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    // Non-draft app PRs run the complete :app:lintRelease check before merge.
    // For CI prereleases, avoid the duplicate lintVitalRelease pulled in by
    // :app:assembleRelease (4m04s of work in the sampled CI build).
    // Normal stable/local release builds keep Android's fatal release lint gate.
    lint {
        checkReleaseBuilds = ciBuildNumber == 0
    }

    buildFeatures { compose = true; buildConfig = true }
    providers.gradleProperty("installedReleaseNotesDir").orNull?.let {
        sourceSets.getByName("main").assets.srcDir(it)
    }
    val packagedNeteaseLibraries = mapOf(
        "libncmusbaudio.so" to "150daa62df3b2f5a9b9bb5d54a3108e90495e50853c4b5ac7df3f63137f70a7a",
        "libc++_shared.so" to "3e6d529147ffd66bd31fab85c8902b526abd1c06e43554ce64b97519e255e4b3")
    packagedNeteaseLibraries.forEach { (name, expected) ->
        val binary = file("src/main/jniLibs/arm64-v8a/$name")
        check(binary.isFile && MessageDigest.getInstance("SHA-256").digest(binary.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 255) } == expected) {
            "Bundled NetEase runtime failed verification: $name"
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
        if (rootProject.findProject(":baselineprofile") != null) create("performance") {
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) } }
    packaging { resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/AL2.0", "META-INF/LGPL2.1") }
    // Extract version-bound vendor libraries to the app's native library directory.
    packaging.jniLibs.useLegacyPackaging = true
    // These exact, version-pinned vendor bytes are verified at configuration and import.
    // Stripping them would invalidate their ABI/hash contract. Our own JNI libraries can
    // be stripped normally; their symbol artifacts remain in the build output.
    packaging.jniLibs.keepDebugSymbols += setOf(
        "**/libncmusbaudio.so", "**/libc++_shared.so")
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
        if (rootProject.findProject(":baselineprofile") != null) create("performance") { from(project(":baselineprofile")) }
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

configurations.configureEach {
    resolutionStrategy.force(
        "androidx.lifecycle:lifecycle-runtime-compose:2.10.0",
        "androidx.lifecycle:lifecycle-runtime-compose-android:2.10.0",
        "androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0",
        "androidx.lifecycle:lifecycle-viewmodel-compose-android:2.10.0",
    )
}

dependencies {
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
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
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.0")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation("androidx.compose.material:material-icons-core:1.7.8")

    implementation("io.github.suqi8.coui.kmp:coui-ui-android:2.1.0")
    implementation("io.github.suqi8.coui.kmp:coui-blur-android:2.1.0")
    implementation("io.github.suqi8.coui.kmp:coui-icons-android:2.1.0")
    implementation("io.github.suqi8.coui.kmp:coui-preference-android:2.1.0")
    implementation("io.github.suqi8.coui.kmp:coui-squircle-android:2.1.0")
    implementation("com.materialkolor:material-color-utilities:4.1.1")

    implementation("io.github.suqi8.coui.kmp:coui-nav-android:2.1.0")
    implementation("androidx.navigationevent:navigationevent-compose:1.2.0")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.eclipse.paho:org.eclipse.paho.mqttv5.client:1.2.5")

    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.work:work-testing:2.12.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.11.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.11.2")
}
