package com.moneymanager.ui.feature.settings

import android.content.Context
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.moneymanager.domain.model.AppResult
import com.moneymanager.domain.repository.BackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/** Outcome of a Drive backup/restore attempt. */
sealed interface DriveResult {
    data object Success : DriveResult

    /** No backup file exists in Drive yet (restore only). */
    data object NoBackup : DriveResult

    /**
     * The user must grant the Drive scope. Launch [intentSender] with
     * StartIntentSenderForResult, then retry the same action on RESULT_OK.
     */
    data class ConsentRequired(val intentSender: IntentSender) : DriveResult

    data class Error(val message: String) : DriveResult
}

/**
 * Backs the app's JSON snapshot up to the Google Drive **appDataFolder** (hidden,
 * app-scoped — no broad Drive access), and restores it.
 *
 * Auth uses Google Identity Services [com.google.android.gms.auth.api.identity.AuthorizationClient]
 * to obtain an OAuth access token for the `drive.appdata` scope; the first grant
 * needs user consent (surfaced as [DriveResult.ConsentRequired]), after which the
 * token is returned silently. Drive itself is called over its REST v3 API.
 *
 * Lives in the ui layer (not data/) so it can reuse the domain [BackupRepository]
 * for the pure JSON payload while owning the Android/networking integration; the
 * ui layer never imports data/.
 */
class GoogleDriveBackup
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val backupRepository: BackupRepository,
    ) {
        private val authorizationClient = Identity.getAuthorizationClient(context)
        private val http = OkHttpClient()
        private val json = Json { ignoreUnknownKeys = true }

        suspend fun backup(): DriveResult =
            withToken { token ->
                when (val exported = backupRepository.exportToJson()) {
                    is AppResult.Failure -> DriveResult.Error("Could not read data to back up")
                    is AppResult.Success -> {
                        val existingId = findBackupFileId(token)
                        if (existingId == null) {
                            createBackup(token, exported.data)
                        } else {
                            updateBackup(token, existingId, exported.data)
                        }
                        DriveResult.Success
                    }
                }
            }

        suspend fun restore(): DriveResult =
            withToken { token ->
                val fileId = findBackupFileId(token) ?: return@withToken DriveResult.NoBackup
                val text = downloadBackup(token, fileId)
                when (backupRepository.importFromJson(text)) {
                    is AppResult.Success -> DriveResult.Success
                    is AppResult.Failure -> DriveResult.Error("Backup file could not be restored")
                }
            }

        /** Acquire a token (prompting for consent if needed) then run [block]. */
        private suspend fun withToken(block: suspend (String) -> DriveResult): DriveResult =
            withContext(Dispatchers.IO) {
                runCatching {
                    val request =
                        AuthorizationRequest.builder()
                            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
                            .build()
                    val result = Tasks.await(authorizationClient.authorize(request))
                    val pendingIntent = result.pendingIntent
                    when {
                        result.hasResolution() && pendingIntent != null ->
                            DriveResult.ConsentRequired(pendingIntent.intentSender)
                        result.accessToken != null -> block(result.accessToken!!)
                        else -> DriveResult.Error("Google sign-in did not return an access token")
                    }
                }.getOrElse { DriveResult.Error(it.message ?: "Google Drive request failed") }
            }

        private fun findBackupFileId(token: String): String? {
            val url =
                "$DRIVE_V3/files?spaces=appDataFolder" +
                    "&q=${"name = '$BACKUP_FILE_NAME'".encodeQuery()}" +
                    "&fields=files(id,name)"
            http.newCall(get(url, token)).execute().use { response ->
                if (!response.isSuccessful) error(driveError("list", response.code))
                val body = response.body.string()
                return json.decodeFromString<FileList>(body).files.firstOrNull()?.id
            }
        }

        private fun createBackup(token: String, content: String) {
            val metadata =
                json.encodeToString(
                    CreateMetadata(name = BACKUP_FILE_NAME, parents = listOf("appDataFolder")),
                )
            val multipart =
                MultipartBody.Builder()
                    .setType("multipart/related".toMediaType())
                    .addPart(metadata.toRequestBody(JSON_MEDIA))
                    .addPart(content.toRequestBody(JSON_MEDIA))
                    .build()
            val request =
                Request.Builder()
                    .url("$DRIVE_UPLOAD/files?uploadType=multipart&fields=id")
                    .addHeader("Authorization", "Bearer $token")
                    .post(multipart)
                    .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error(driveError("upload", response.code))
            }
        }

        private fun updateBackup(token: String, fileId: String, content: String) {
            val request =
                Request.Builder()
                    .url("$DRIVE_UPLOAD/files/$fileId?uploadType=media")
                    .addHeader("Authorization", "Bearer $token")
                    .patch(content.toRequestBody(JSON_MEDIA))
                    .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error(driveError("update", response.code))
            }
        }

        private fun downloadBackup(token: String, fileId: String): String {
            http.newCall(get("$DRIVE_V3/files/$fileId?alt=media", token)).execute().use { response ->
                if (!response.isSuccessful) error(driveError("download", response.code))
                return response.body.string()
            }
        }

        private fun get(url: String, token: String): Request =
            Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

        private fun driveError(op: String, code: Int): String = "Drive $op failed (HTTP $code)"

        @Serializable
        private data class FileList(val files: List<DriveFile> = emptyList())

        @Serializable
        private data class DriveFile(val id: String, val name: String)

        @Serializable
        private data class CreateMetadata(val name: String, val parents: List<String>)

        private companion object {
            const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
            const val DRIVE_V3 = "https://www.googleapis.com/drive/v3"
            const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3"
            const val BACKUP_FILE_NAME = "money-manager-backup.json"
            val JSON_MEDIA = "application/json".toMediaType()
        }
    }

/** Percent-encode a Drive `q` parameter value. */
private fun String.encodeQuery(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
