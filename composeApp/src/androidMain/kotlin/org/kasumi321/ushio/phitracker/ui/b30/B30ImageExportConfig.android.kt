package org.kasumi321.ushio.phitracker.ui.b30

import coil3.request.ImageRequest
import coil3.request.allowHardware

actual fun ImageRequest.Builder.setImageRequestAllowHardware(allow: Boolean): ImageRequest.Builder =
    this.allowHardware(allow)
