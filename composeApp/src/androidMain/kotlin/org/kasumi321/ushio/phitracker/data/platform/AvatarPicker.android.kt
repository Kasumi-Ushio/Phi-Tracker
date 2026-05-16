package org.kasumi321.ushio.phitracker.data.platform

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import java.io.File

private fun copyAvatarToPrivateStorage(context: Context, sourceUri: Uri): String? {
    return runCatching {
        val avatarDir = File(context.filesDir, "avatar").apply { mkdirs() }
        val targetFile = File(avatarDir, "avatar_${System.currentTimeMillis()}.jpg")

        context.contentResolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "Failed to open avatar input stream." }
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        avatarDir.listFiles()
            ?.sortedByDescending { file -> file.lastModified() }
            ?.drop(3)
            ?.forEach { file -> file.delete() }

        Uri.fromFile(targetFile).toString()
    }.getOrNull()
}

@Composable
actual fun rememberAvatarPicker(onResult: (String?) -> Unit): () -> Unit {
    val context = AndroidPlatformContext.applicationContext
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (context != null) {
                onResult(copyAvatarToPrivateStorage(context, it))
            } else {
                onResult(null)
            }
        } ?: onResult(null)
    }
    return { launcher.launch("image/*") }
}
