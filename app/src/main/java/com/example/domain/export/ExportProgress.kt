package com.example.domain.export

enum class ExportState {
    IDLE,
    PREPARING,
    EXPORTING,
    ZIPPING,
    UPLOADING,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class ExportProgress(
    val state: ExportState = ExportState.IDLE,
    val type: ExportType? = null,
    val percentage: Int = 0,
    val currentPart: Int = 0,
    val totalParts: Int = 0,
    val statusMessage: String = "",
    val uploadedFiles: List<String> = emptyList(),
    val totalSizeBytes: Long = 0L,
    val error: String? = null
) {
    val isRunning: Boolean get() = state in listOf(
        ExportState.PREPARING,
        ExportState.EXPORTING,
        ExportState.ZIPPING,
        ExportState.UPLOADING
    )
}
