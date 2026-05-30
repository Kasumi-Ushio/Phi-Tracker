package org.kasumi321.ushio.phitracker.data.platform

import androidx.compose.runtime.Composable

@Composable
expect fun rememberB30BackgroundPicker(onResult: (String?) -> Unit): () -> Unit
