#pragma once
#include <chrono>

namespace currentmusic_usb {
constexpr int queueTargetMs(int rate) {
    return rate >= 176400 ? 250 : 100;
}
struct WriteRetryWindow {
    using TimePoint = std::chrono::steady_clock::time_point;
    TimePoint since{};
    int rejections = 0;
    int noteRejection(TimePoint now) {
        if (rejections == 0) since = now;
        return ++rejections;
    }
    bool expired(TimePoint now) const {
        return rejections > 0 && now - since >= std::chrono::milliseconds(500);
    }
    long long elapsedMs(TimePoint now) const {
        return rejections == 0 ? 0 : std::chrono::duration_cast<std::chrono::milliseconds>(now - since).count();
    }
    void noteAccepted() {
        since = {};
        rejections = 0;
    }
};
}
