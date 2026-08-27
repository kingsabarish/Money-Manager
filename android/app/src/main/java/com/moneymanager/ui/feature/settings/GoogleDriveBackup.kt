package com.moneymanager.ui.feature.settings

import android.content.Context
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import java.time.Instant
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

    /** No backup file exists in Drive yet (restore/delete only). */
    data object NoBackup : DriveResult

    /** One or more timestamped backups exist; the UI should let the user pick one. */
    data class BackupsAvailable(val entries: List<BackupEntry>) : DriveResult

    /**
     * The user must grant the Drive scope. Launch [intentSender] with
     * StartIntentSenderForResult, then retry the same action on RESULT_OK.
     */
    data class ConsentRequired(val intentSender: IntentSender) : DriveResult

    data class Error(val message: String) : DriveResult
}

/** A single timestamped backup file in Drive's appDataFolder. */
data class BackupEntry(
    val id: String,
    val timestampEpochMs: Long,
)

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
                        val ts = System.currentTimeMillis()
                        createBackup(token, "$BACKUP_FILE_PREFIX-$ts.json", exported.data)
                        enforceLimit(token)
                        DriveResult.Success
                    }
                }
            }

        /** List the timestamped backups available in Drive, newest first. */
        suspend fun listBackups(): DriveResult =
            withToken { token ->
                val files = listBackupFiles(token)
                if (files.isEmpty()) return@withToken DriveResult.NoBackup
                val entries =
                    files
                        .map { BackupEntry(it.id, timestampOf(it.name, it.createdTime)) }
                        .sortedByDescending { it.timestampEpochMs }
                DriveResult.BackupsAvailable(entries)
            }

        /** Restore a specific backup by its Drive file id. */
        suspend fun restore(fileId: String): DriveResult =
            withToken { token ->
                val text = downloadBackup(token, fileId)
                when (backupRepository.importFromJson(text)) {
                    is AppResult.Success -> DriveResult.Success
                    is AppResult.Failure -> DriveResult.Error("Backup file could not be restored")
                }
            }

        /** Delete all backup files from the Drive appDataFolder. */
        suspend fun delete(): DriveResult =
            withToken { token ->
                val files = listBackupFiles(token)
                if (files.isEmpty()) return@withToken DriveResult.NoBackup
                files.forEach { deleteBackup(token, it.id) }
                DriveResult.Success
            }

        private fun deleteBackup(token: String, fileId: String) {
            val request =
                Request.Builder()
                    .url("$DRIVE_V3/files/$fileId")
                    .addHeader("Authorization", "Bearer $token")
                    .delete()
                    .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error(driveError("delete", response.code))
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

        private fun listBackupFiles(token: String): List<DriveFile> {
            val url =
                "$DRIVE_V3/files?spaces=appDataFolder" +
                    "&q=${"name contains '$BACKUP_FILE_PREFIX'".encodeQuery()}" +
                    "&fields=files(id,name,createdTime)"
            http.newCall(get(url, token)).execute().use { response ->
                if (!response.isSuccessful) error(driveError("list", response.code))
                val body = response.body.string()
                return json.decodeFromString<FileList>(body).files
            }
        }

        private fun timestampOf(name: String, createdTime: String): Long {
            val match = TIMESTAMP_RE.matchEntire(name)
            if (match != null) return match.groupValues[1].toLongOrNull() ?: 0L
            return runCatching { Instant.parse(createdTime).toEpochMilli() }.getOrDefault(0L)
        }

        private fun enforceLimit(token: String) {
            val files = listBackupFiles(token).sortedByDescending { timestampOf(it.name, it.createdTime) }
            files.drop(MAX_BACKUPS).forEach { deleteBackup(token, it.id) }
        }

        private fun createBackup(token: String, fileName: String, content: String) {
            val metadata =
                json.encodeToString(
                    CreateMetadata(name = fileName, parents = listOf("appDataFolder")),
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
        private data class DriveFile(
            val id: String,
            val name: String,
            val createdTime: String = "",
        )

        @Serializable
        private data class CreateMetadata(val name: String, val parents: List<String>)

        private companion object {
            const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
            const val DRIVE_V3 = "https://www.googleapis.com/drive/v3"
            const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3"
            const val BACKUP_FILE_PREFIX = "money-manager-backup"
            const val MAX_BACKUPS = 10
            private val TIMESTAMP_RE = Regex("""money-manager-backup-(\d+)\.json""")
            val JSON_MEDIA = "application/json".toMediaType()
        }
    }

/** Percent-encode a Drive `q` parameter value. */
private fun String.encodeQuery(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
