package org.kasumi321.ushio.phitracker.data.platform

import platform.UIKit.UIPasteboard

actual fun copyToClipboard(label: String, text: String) {
    UIPasteboard.generalPasteboard.string = text
}
