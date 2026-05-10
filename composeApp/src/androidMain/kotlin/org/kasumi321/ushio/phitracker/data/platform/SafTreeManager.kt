package org.kasumi321.ushio.phitracker.data.platform

import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages the SAF (Storage Access Framework) tree URI lifecycle for API 28 and below.
 *
 * On first save, prompts the user to select a directory via `OpenDocumentTree`.
 * Stores the tree URI and persisted write permission in private SharedPreferences.
 * Subsequent saves reuse the persisted URI as long as write permission is still valid.
 * If permission is revoked or URI is stale, clears storage and re-prompts.
 *
 * Must be initialized from [MainActivity.onCreate] via [initialize].
 */
class SafTreeManager private constructor() {

    private var launcher: androidx.activity.result.ActivityResultLauncher<Uri?>? = null

    @Volatile
    private var pendingContinuation: CancellableContinuation<Uri?>? = null

    private fun onPickerResult(uri: Uri?) {
        if (uri != null) {
            val context = AndroidPlatformContext.applicationContext
            if (context != null) {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            }
            persistTreeUri(uri.toString())
        }
        val cont = pendingContinuation
        pendingContinuation = null
        if (cont?.isActive == true) {
            cont.resume(uri)
        }
    }

    /**
     * Launches the SAF directory picker and suspends until the user selects a tree or cancels.
     *
     * @return The selected tree [Uri], or `null` if the user cancelled.
     * @throws IllegalStateException if a picker is already active.
     */
    private suspend fun launchPicker(): Uri? = suspendCancellableCoroutine { cont ->
        val l = launcher
            ?: run {
                cont.resumeWithException(IllegalStateException("SafTreeManager not initialized"))
                return@suspendCancellableCoroutine
            }
        synchronized(this) {
            if (pendingContinuation != null) {
                cont.resumeWithException(IllegalStateException("Directory picker already in progress"))
                return@suspendCancellableCoroutine
            }
            pendingContinuation = cont
        }
        cont.invokeOnCancellation {
            pendingContinuation = null
        }
        l.launch(null)
    }

    /**
     * Ensures a valid tree URI is available, launching the picker if necessary.
     * Must be called from the main thread because the picker is a UI operation.
     *
     * @return A valid, write-permitted tree [Uri].
     * @throws IOException if the user cancels the picker.
     */
    suspend fun ensureTreeUri(): Uri {
        val stored = getPersistedTreeUri()
        if (stored != null && isTreeUriValid(stored)) {
            return stored
        }
        if (stored != null) {
            clearPersistedTreeUri()
        }
        return launchPicker() ?: throw IOException("User cancelled directory selection")
    }

    /**
     * Saves a [Bitmap] as a PNG document under the SAF tree directory.
     *
     * Handles threading: bitmap compression and I/O run on [Dispatchers.IO],
     * while the directory picker (if needed) runs on the main thread.
     *
     * @param bitmap  The bitmap to save.
     * @param fileName  The destination filename (e.g. "song_hq.png").
     * @return [Result.success] on success, [Result.failure] on error.
     */
    suspend fun saveBitmap(bitmap: Bitmap, fileName: String): Result<Unit> = runCatching {
        val context = AndroidPlatformContext.applicationContext
            ?: throw IllegalStateException("Android context not initialized")

        val treeUri = ensureTreeUri()

        withContext(Dispatchers.IO) {
            val contentResolver = context.contentResolver
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )

            deleteExistingDocument(contentResolver, treeUri, fileName)

            val docUri = DocumentsContract.createDocument(
                contentResolver,
                parentDocUri,
                "image/png",
                fileName
            )
            requireNotNull(docUri) {
                "Failed to create document '$fileName' in the selected directory"
            }

            contentResolver.openOutputStream(docUri).use { output ->
                requireNotNull(output) { "Unable to open SAF document output for '$fileName'" }
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Bitmap compression to PNG failed for '$fileName'"
                }
            }
        }
    }

    private fun prefs(): SharedPreferences {
        val context = AndroidPlatformContext.applicationContext
            ?: throw IllegalStateException("AndroidPlatformContext.applicationContext is null")
        return context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }

    private fun getPersistedTreeUri(): Uri? {
        val str = prefs().getString(KEY_TREE_URI, null) ?: return null
        return Uri.parse(str)
    }

    private fun persistTreeUri(uriString: String) {
        prefs().edit().putString(KEY_TREE_URI, uriString).apply()
    }

    private fun clearPersistedTreeUri() {
        prefs().edit().remove(KEY_TREE_URI).apply()
    }

    /**
     * Validates that the persisted tree URI still has write permission.
     */
    private fun isTreeUriValid(treeUri: Uri): Boolean {
        val context = AndroidPlatformContext.applicationContext ?: return false
        val permissions = context.contentResolver.persistedUriPermissions
        return permissions.any { perm ->
            perm.uri == treeUri && perm.isWritePermission
        }
    }

    /**
     * Attempts to find and delete an existing document with [fileName] under [treeUri].
     * No-op if the document does not exist.
     */
    private fun deleteExistingDocument(
        contentResolver: ContentResolver,
        treeUri: Uri,
        fileName: String
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        // Query without selection - some DocumentsProvider implementations do not
        // reliably honor SQL-style selection args. We filter client-side instead.
        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameIndex)
                if (displayName == fileName) {
                    val existingId = cursor.getString(idIndex)
                    val existingUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, existingId)
                    DocumentsContract.deleteDocument(contentResolver, existingUri)
                }
            }
        }
    }

    // ── Singleton lifecycle ────────────────────────────────────────────────

    companion object {
        private const val PREFS_NAME = "saf_tree_prefs"
        private const val KEY_TREE_URI = "tree_uri"

        @Volatile
        private var instance: SafTreeManager? = null

        /**
         * Initialize the SAF manager with the host [activity].
         *
         * Must be called during [ComponentActivity.onCreate] — this registers the
         * `OpenDocumentTree` activity-result launcher needed to prompt for a directory.
         *
         * Safe to call multiple times (e.g. on activity re-creation); each call
         * replaces the previous launcher with a freshly-registered one.
         */
        fun initialize(activity: ComponentActivity) {
            val manager = SafTreeManager()
            manager.launcher = activity.registerForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri -> manager.onPickerResult(uri) }
            instance = manager
        }

        /** @throws IllegalStateException if [initialize] was never called. */
        fun getInstance(): SafTreeManager =
            instance ?: throw IllegalStateException(
                "SafTreeManager not initialized. Call SafTreeManager.initialize(activity) in MainActivity.onCreate."
            )
    }
}
