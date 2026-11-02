#pragma once
#include <cstdint>
#include <limits>
#include <memory>
#include <mutex>
#include <unordered_map>

namespace currentmusic_audio {
// JNI receives monotonically allocated tokens, never native addresses. A lookup keeps
// the object alive until the calling operation finishes, even if release races it.
// This mutex is never acquired by the realtime audio callback.
template<class T> class HandleRegistry {
    std::mutex mutex_;
    std::unordered_map<int64_t, std::shared_ptr<T>> entries_;
    int64_t sequence_ = 0;
public:
    int64_t add(std::shared_ptr<T> value) {
        if (!value) return 0;
        std::lock_guard<std::mutex> lock(mutex_);
        if (sequence_ == std::numeric_limits<int64_t>::max()) return 0;
        const auto id = ++sequence_;
        entries_.emplace(id, std::move(value));
        return id;
    }
    std::shared_ptr<T> get(int64_t id) {
        std::lock_guard<std::mutex> lock(mutex_);
        const auto found = entries_.find(id);
        return found == entries_.end() ? nullptr : found->second;
    }
    void remove(int64_t id) {
        std::shared_ptr<T> removed;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            const auto found = entries_.find(id);
            if (found == entries_.end()) return;
            removed = std::move(found->second);
            entries_.erase(found);
        }
        // A native stream destructor may block or trigger callbacks. Never run it
        // while holding the registry lock.
    }
};
}
