cmake_minimum_required(VERSION 3.22)
project(currentmusic_output LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(BUILD_SHARED_LIBS OFF)
add_subdirectory(oboe)
add_library(currentmusic_output SHARED audio_output.cpp)
target_link_libraries(currentmusic_output PRIVATE oboe android log)
target_link_options(currentmusic_output PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,--exclude-libs,ALL")

add_library(currentmusic_quickjs SHARED
    lx_quickjs_jni.cpp
    quickjs/quickjs.c
    quickjs/cutils.c
    quickjs/dtoa.c
    quickjs/libregexp.c
    quickjs/libunicode.c)
file(STRINGS quickjs/VERSION QUICKJS_VERSION LIMIT_COUNT 1)
target_compile_definitions(currentmusic_quickjs PRIVATE _GNU_SOURCE CONFIG_VERSION="${QUICKJS_VERSION}")
target_compile_options(currentmusic_quickjs PRIVATE $<$<COMPILE_LANGUAGE:C>:-fwrapv>)
set_target_properties(currentmusic_quickjs PROPERTIES
    C_STANDARD 11
    C_STANDARD_REQUIRED ON
    C_VISIBILITY_PRESET hidden
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON)
target_link_libraries(currentmusic_quickjs PRIVATE m)
target_link_options(currentmusic_quickjs PRIVATE
    "-Wl,-z,max-page-size=16384" "-Wl,--exclude-libs,ALL" "-Wl,--no-undefined")

# The adapter uses the shared C++ runtime of the pinned NetEase arm64 engine.
# The Oboe backend retains its existing static runtime.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_library(currentmusic_netease_usb SHARED netease_usb.cpp)
    target_link_options(currentmusic_netease_usb PRIVATE "-nostdlib++" "-Wl,-z,max-page-size=16384" "-Wl,--no-undefined")
    target_link_libraries(currentmusic_netease_usb PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/arm64-v8a/libc++_shared.so" dl log)
endif()
