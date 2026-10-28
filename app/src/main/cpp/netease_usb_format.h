#pragma once
#include <cstddef>
#include <cstdint>
#include <vector>

namespace currentmusic_usb {
// Public libusb descriptor ABI and the pinned engine's public GetInfos result.
// These are returned values, never offsets into the engine's private objects.
struct InterfaceDescriptor {
    uint8_t length, type, number, alternate, endpoints, deviceClass, subclass, protocol, stringIndex;
    const void* endpoint;
    const uint8_t* extra;
    int extraLength;
};
struct Interface { const InterfaceDescriptor* alternate; int count; };
struct Config {
    uint8_t length, type;
    uint16_t totalLength;
    uint8_t interfaceCount, value, stringIndex, attributes, power;
    const Interface* interfaces;
    const uint8_t* extra;
    int extraLength;
};
struct StreamingInfo {
    uint8_t interfaceId = 0, alternate = 0;
    uint16_t reserved = 0;
    uint32_t currentRate = 0;
    uint8_t channels = 0, validBits = 0, subslotBytes = 0;
    uint8_t padding[5]{};
    std::vector<uint32_t> rates;
    uint32_t uacFormat = 0, reserved2 = 0;
    uint64_t currentBuffer = 0;
    bool available = false;
    uint8_t tail[7]{};
};
static_assert(sizeof(Config) == 40 && offsetof(Config, interfaces) == 16);
static_assert(sizeof(Interface) == 16 && sizeof(InterfaceDescriptor) == 40);
static_assert(sizeof(StreamingInfo) == 64 && offsetof(StreamingInfo, rates) == 16 &&
              offsetof(StreamingInfo, uacFormat) == 40);

// The engine's IsSupportAudioFormat deliberately skips validBits==24, even for
// a four-byte USB subslot. Validate both dimensions instead of accepting a false
// rejection or silently truncating 32-bit source samples to 24 valid bits.
inline bool accepts(const StreamingInfo& info, int rate, int channels, int wireBytes, int sourceBits) {
    if (rate < 8000 || rate > 192000 || channels < 1 || channels > 2 ||
        (wireBytes != 2 && wireBytes != 4) || (sourceBits != 16 && sourceBits != 24 && sourceBits != 32)) return false;
    if (info.uacFormat != 1 || info.channels != channels || info.subslotBytes != wireBytes ||
        info.validBits < sourceBits || info.validBits > wireBytes * 8) return false;
    for (auto allowed : info.rates) if (allowed == static_cast<uint32_t>(rate)) return true;
    return false;
}
inline int select(const std::vector<StreamingInfo>& infos, int rate, int channels, int wireBytes, int sourceBits) {
    // Prefer the wider valid-bit candidate if both retain all source precision.
    int selected = -1;
    for (size_t i = 0; i < infos.size(); ++i) {
        if (accepts(infos[i], rate, channels, wireBytes, sourceBits) &&
            (selected < 0 || infos[i].validBits > infos[selected].validBits)) selected = static_cast<int>(i);
    }
    return selected;
}
}
