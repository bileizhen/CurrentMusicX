#include "../../main/cpp/audio_output_registry.h"
#include <atomic>
#include <cassert>
#include <functional>
#include <thread>
#include <vector>

struct Object {
    std::atomic<int>& destroyed;
    std::atomic<int> accesses{0};
    std::function<void()> onDestroy;
    explicit Object(std::atomic<int>& counter) : destroyed(counter) {}
    ~Object() { if (onDestroy) onDestroy(); ++destroyed; }
};
int main() {
    currentmusic_audio::HandleRegistry<Object> handles;
    std::atomic<int> destroyed{0};
    assert(!handles.get(0)); assert(!handles.get(-1));
    const auto id = handles.add(std::make_shared<Object>(destroyed));
    auto held = handles.get(id);
    handles.remove(id); handles.remove(id);
    assert(!handles.get(id)); assert(destroyed == 0);
    const auto next = handles.add(std::make_shared<Object>(destroyed));
    assert(id != next); assert(!handles.get(id));
    held.reset(); assert(destroyed == 1);
    auto reentrant = handles.get(next);
    reentrant->onDestroy = [&] { assert(!handles.get(next)); };
    reentrant.reset(); handles.remove(next); assert(destroyed == 2);

    std::atomic<int64_t> current{0}; std::atomic<bool> stop{false};
    std::vector<std::thread> readers;
    for (int i = 0; i < 4; ++i) readers.emplace_back([&] {
        while (!stop.load()) if (auto object = handles.get(current.load())) ++object->accesses;
    });
    for (int i = 0; i < 10000; ++i) {
        const auto token = handles.add(std::make_shared<Object>(destroyed));
        current.store(token); handles.remove(token);
    }
    stop = true;
    for (auto& thread : readers) thread.join();
    assert(destroyed == 10002); assert(!handles.get(current.load()));
}
