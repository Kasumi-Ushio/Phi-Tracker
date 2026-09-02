package org.kasumi321.ushio.phitracker.ui.settings

/** Shared constants for the Overflow slider in Settings. */
object SettingsConstants {
    /** Minimum overflow count. */
    const val OVERFLOW_COUNT_MIN: Int = 1

    /** Maximum overflow count. */
    const val OVERFLOW_COUNT_MAX: Int = 30

    /** Number of steps between min and max (max - min - 1). */
    const val OVERFLOW_SLIDER_STEPS: Int = OVERFLOW_COUNT_MAX - OVERFLOW_COUNT_MIN - 1

    /** Minimum haze blur strength multiplier. */
    const val HAZE_STRENGTH_MIN: Float = 0.5f

    /** Maximum haze blur strength multiplier. */
    const val HAZE_STRENGTH_MAX: Float = 2f

    /** Number of steps between min and max at 0.25 increments. */
    const val HAZE_SLIDER_STEPS: Int = ((HAZE_STRENGTH_MAX - HAZE_STRENGTH_MIN) / 0.25f).toInt() - 1
}
