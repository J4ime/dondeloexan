package com.dondeloexan.domain.model

sealed interface BackupState {
    data object Idle : BackupState
    data object Exporting : BackupState
    data class ExportSuccess(val count: Int) : BackupState
    data object Importing : BackupState
    data class ImportSuccess(val count: Int) : BackupState
    data class Error(val message: String) : BackupState
}
