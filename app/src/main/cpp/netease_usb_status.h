#pragma once

namespace currentmusic_usb {
// NetEase 9.6.15 GetXferStatus, NOT libusb_transfer_status and NOT Start's
// return code. Verified against startStreamingThread and completion callbacks.
enum class TransferState { Idle = 0, Running = 1, Timeout = 2, Error = 3, Unknown = -1 };
constexpr TransferState transferState(int value) {
    switch (value) {
        case 0: return TransferState::Idle;
        case 1: return TransferState::Running;
        case 2: return TransferState::Timeout;
        case 3: return TransferState::Error;
        default: return TransferState::Unknown;
    }
}
constexpr bool transferHealthy(int value, bool detached) {
    return !detached && (transferState(value) == TransferState::Idle || transferState(value) == TransferState::Running);
}
constexpr const char* transferLabel(int value) {
    switch (transferState(value)) {
        case TransferState::Idle: return "IDLE";
        case TransferState::Running: return "RUNNING";
        case TransferState::Timeout: return "TIMEOUT";
        case TransferState::Error: return "ERROR";
        default: return "UNKNOWN";
    }
}
}
