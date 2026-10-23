Oboe 1.10.0, https://github.com/google/oboe
Commit a81bb9f87d4105b84b682685d3bfbb5beca371d1.
Only the library source, public headers, CMake build and Apache-2.0 LICENSE are vendored.
Local changes: AAudioLoader's compatibility declarations exclude NDK 30, which now
ships these types; its device enum ABI check accepts the NDK 30 int32-backed enum.
Linked statically into currentmusic_output with a private C++ runtime.
