package com.yxm.drive

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.Collections
import java.util.concurrent.Executors


class DriveViewModel(private val driveService: Drive) : ViewModel() {

    private val executors = Executors.newCachedThreadPool()

    /**
     * Creates a text file in the user's My Drive folder and returns its file ID.
     * Tasks.call已废弃，可用TaskCompletionSource代替
     */
    fun createFile(): Task<String> {
        return Tasks.call(executors) {
            val metaFile = File().setParents(Collections.singletonList("root"))
                .setMimeType("text/plain")
                .setName("Test file")
            val googleFile: File? = driveService.files().create(metaFile).execute()
            return@call googleFile?.id
        }
    }

    /**
     * Opens the file identified by {@code fileId} and returns a {@link Pair} of its name and
     * contents.
     */
    fun readFile(fileId: String): Task<Pair<String, String>> {
        return Tasks.call(executors) {
            val metaFile = driveService.files().get(fileId).execute()
            val name = metaFile.name
            // Stream the file contents to a String.
            driveService.files().get(fileId).executeMediaAsInputStream().use { input ->
                val content = BufferedReader(InputStreamReader(input)).use { reader ->
                    val stringBuilder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line)
                    }
                    stringBuilder.toString()
                }
                return@call Pair(name, content)
            }
        }
    }

    /**
     * Updates the file identified by `fileId` with the given `name` and `content`.
     */
    fun saveFile(fileId: String, name: String, content: String): Task<Void>? {
        return Tasks.call(executors) {
            // Create a File containing any metadata changes.
            val metadata =
                File().setName(name)
            // Convert content to an AbstractInputStreamContent instance.
            val contentStream =
                ByteArrayContent.fromString("text/plain", content)
            // Update the metadata and contents.
            driveService.files().update(fileId, metadata, contentStream).execute()
            null
        }
    }

    /**
     * Returns a [FileList] containing all the visible files in the user's My Drive.
     *
     *
     * The returned list will only contain files visible to this app, i.e. those which were
     * created by this app. To perform operations on files not created by the app, the project must
     * request Drive Full Scope in the [Google
 * Developer's Console](https://play.google.com/apps/publish) and be submitted to Google for verification.
     */
    fun queryFiles(): Task<FileList?> {
        return Tasks.call(executors) {
            driveService.files().list().setSpaces("drive").execute()
        }
    }

    /**
     * Returns an [Intent] for opening the Storage Access Framework file picker.
     */
    fun createFilePickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
        }
    }

    /**
     * Opens the file at the `uri` returned by a Storage Access Framework [Intent]
     * created by [.createFilePickerIntent] using the given `contentResolver`.
     */
    fun openFileUsingStorageAccessFramework(
        contentResolver: ContentResolver, uri: Uri
    ): Task<Pair<String, String>>? {
        return Tasks.call(executors) {
            // Retrieve the document's display name from its metadata.
            var name: String
            contentResolver.query(uri, null, null, null, null).use { cursor ->
                name = if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    cursor.getString(nameIndex)
                } else {
                    throw IOException("Empty cursor returned for file.")
                }
            }
            // Read the document's contents as a String.
            var content: String
            contentResolver.openInputStream(uri).use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    val stringBuilder = java.lang.StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line)
                    }
                    content = stringBuilder.toString()
                }
            }
            return@call Pair(name, content)
        }
    }

}