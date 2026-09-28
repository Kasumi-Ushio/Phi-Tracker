package org.kasumi321.ushio.phitracker.ui.utils

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Locale-independent text holder carried by ViewModel state, so ViewModels
 * never embed user-facing literals. UI resolves it via [asString] in
 * composition or [resolve] from a coroutine (snackbar, platform toast).
 * Args may themselves be [UiText] and are resolved recursively.
 */
sealed interface UiText {
    data class Raw(val value: String) : UiText
    data class Res(val resource: StringResource, val args: List<Any> = emptyList()) : UiText {
        constructor(resource: StringResource, vararg args: Any) : this(resource, args.toList())
    }
}

suspend fun UiText.resolve(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> {
        val resolvedArgs = args.map { if (it is UiText) it.resolve() else it }.toTypedArray()
        if (resolvedArgs.isEmpty()) getString(resource) else getString(resource, *resolvedArgs)
    }
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> {
        val resolvedArgs = args.map { if (it is UiText) it.asString() else it }.toTypedArray()
        if (resolvedArgs.isEmpty()) stringResource(resource) else stringResource(resource, *resolvedArgs)
    }
}
