package org.kasumi321.ushio.phitracker.ui.b30

/**
 * Single source of truth for B30 export dimensions.
 *
 * All export layout constants are derived from this spec to prevent drift
 * between the builder, layout, and platform renderers.
 *
 * Formula matches the beta5 Android baseline:
 * - contentWidthDp = WIDTH_PX / DENSITY - PAGE_PADDING_DP * 2
 * - cardWidthDp = (contentWidthDp - CARD_GAP_DP * 2) / 3
 * - cardHeightDp = cardWidthDp / CARD_ASPECT
 */
object B30ExportSpec {
    // ─── beta5 baseline derived constants ───────────────────────────────
    // These are computed from the authoritative WIDTH_PX / DENSITY pair
    // using the beta5 formula and MUST NOT drift across platform renderers.
    const val WIDTH_PX = 2400
    const val DENSITY = 2.6666667f
    const val FONT_SCALE = 1f
    const val PAGE_PADDING_DP = 16f
    const val CARD_GAP_DP = 9.6f
    const val CARD_ASPECT = 4f
    const val HEADER_HEIGHT_DP = 100f

    /** Usable content width after page padding, in dp. */
    val contentWidthDp: Float
        get() = WIDTH_PX / DENSITY - PAGE_PADDING_DP * 2

    /** Width of a single score card (3-column grid), in dp. */
    val cardWidthDp: Float
        get() = (contentWidthDp - CARD_GAP_DP * 2) / 3f

    /** Height of a single score card, derived from aspect ratio, in dp. */
    val cardHeightDp: Float
        get() = cardWidthDp / CARD_ASPECT

    /** Horizontal gap between cards in the grid, in dp. Derived from CARD_GAP_DP. */
    val cardHorizontalGapDp: Float
        get() = CARD_GAP_DP

    // ─── beta5 fixed layout constants ───────────────────────────────────
    // Profile and stats card widths are derived from the card width formula
    // (beta5 baseline: profileCardWidthDp == statsCardWidthDp == cardWidthDp).
    // This keeps the header cards aligned with the first / third card columns.
    // These values MUST NOT be overridden independently by platform renderers.

    /**
     * Profile card width (left side of header row).
     *
     * Derived from [cardWidthDp] following the beta5 baseline formula:
     * both header cards use the same width as the score cards so the
     * profile card aligns with the leftmost card column and the stats
     * card aligns with the rightmost card column.
     */
    val profileCardWidthDp: Float
        get() = cardWidthDp

    /** Profile/stats card height (header row). Derived from authoritative HEADER_HEIGHT_DP. */
    val profileCardHeightDp: Float
        get() = HEADER_HEIGHT_DP

    /**
     * Stats table card width (right side of header row).
     *
     * Derived from [cardWidthDp] following the beta5 baseline formula,
     * same as [profileCardWidthDp].
     */
    val statsCardWidthDp: Float
        get() = cardWidthDp

    /**
     * Vertical gap between card rows in the grid.
     *
     * Authoritative beta5 baseline value: [CARD_GAP_DP].
     * Beta5 uses the same 9.6 dp spacing horizontally and vertically.
     */
    val cardVerticalGapDp: Float
        get() = CARD_GAP_DP

    // ─── beta5 section/footer layout constants ───────────────────────────
    // These values approximate the auto-layout behaviour of the Compose
    // export layout (B30ExportLayout) for platform renderers that use an
    // imperative canvas (e.g. Skia on iOS). They are NOT independent
    // platform heuristics — they derive from the common export layout.
    // Any change here must be reflected in B30ExportLayout to avoid drift.

    /**
     * Section title region height, in dp.
     *
     * Approximates the [B30ExportLayout.SectionTitle] titleMedium typography
     * plus intrinsic padding. The Compose layout does not specify an explicit
     * height here; this value captures the effective visual space.
     *
     * Authoritative beta5 baseline value: **30 dp**.
     */
    val sectionTitleHeightDp: Float
        get() = 30f

    /**
     * Footer region height, in dp.
     *
     * Approximates the auto-height of the footer [Row] in [B30ExportLayout]
     * (titleMedium typography on two text composables).
     *
     * Authoritative beta5 baseline value: **30 dp**.
     */
    val footerHeightDp: Float
        get() = 30f

    /**
     * Vertical offset within the footer region for text baseline, in dp.
     *
     * Positions the footer text roughly centered within [footerHeightDp].
     *
     * Authoritative beta5 baseline value: **11.25 dp**.
     */
    val footerTextOffsetDp: Float
        get() = 11.25f
}
