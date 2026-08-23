package com.moneymanager.domain.repository

import com.moneymanager.domain.model.AppResult

/**
 * Serializes the whole database to/from a portable JSON snapshot. Storage of the
 * snapshot (a local file the user picks, or Google Drive) is a separate concern;
 * this only produces and consumes the JSON text.
 */
interface BackupRepository {
    /** Snapshot the current database as JSON text. */
    suspend fun exportToJson(): AppResult<String>

    /** Replace all data with the contents of [json]. Rejected if the format is unknown. */
    suspend fun importFromJson(json: String): AppResult<Unit>
}
