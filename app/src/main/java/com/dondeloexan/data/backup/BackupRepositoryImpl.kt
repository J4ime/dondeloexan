package com.dondeloexan.data.backup

import android.net.Uri
import com.dondeloexan.domain.repository.BackupRepository

class BackupRepositoryImpl(
    private val backupManager: BackupManager
) : BackupRepository {

    override suspend fun exportBackup(uri: Uri): Result<Unit> = runCatching {
        backupManager.exportBackup(uri)
    }

    override suspend fun importBackup(uri: Uri): Result<Int> = runCatching {
        backupManager.importBackup(uri)
    }
}
