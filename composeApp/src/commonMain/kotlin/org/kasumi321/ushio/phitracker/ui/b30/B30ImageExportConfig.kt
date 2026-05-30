package org.kasumi321.ushio.phitracker.ui.b30

import coil3.request.ImageRequest

/**
 * Sets [ImageRequest.Builder.extras] to control hardware bitmap usage.
 * On Android, disables hardware bitmaps when [allow] is false.
 * On iOS, this is a no-op (no hardware bitmaps).
 */
expect fun ImageRequest.Builder.setImageRequestAllowHardware(allow: Boolean): ImageRequest.Builder
