cmake_minimum_required(VERSION 3.22)
project(currentmusic_output LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(BUILD_SHARED_LIBS OFF)
add_subdirectory(oboe)
add_library(currentmusic_output SHARED audio_output.cpp)
target_link_libraries(currentmusic_output PRIVATE oboe android log)
target_link_options(currentmusic_output PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,--exclude-libs,ALL")

# The adapter uses the shared C++ runtime of the pinned NetEase arm64 engine.
# The Oboe backend retains its existing static runtime.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_library(currentmusic_netease_usb SHARED netease_usb.cpp)
    target_link_options(currentmusic_netease_usb PRIVATE "-nostdlib++" "-Wl,-z,max-page-size=16384" "-Wl,--no-undefined")
    target_link_libraries(currentmusic_netease_usb PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/arm64-v8a/libc++_shared.so" dl log)
endif()
