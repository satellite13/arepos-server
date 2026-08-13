package ru.kavader.arepos.service.modelpackage

import java.util.UUID

object PackageImportStages {
    const val QUEUED = "QUEUED"
    const val VALIDATING = "VALIDATING"
    const val IMPORTING_NOTATIONS = "IMPORTING_NOTATIONS"
    const val IMPORTING_FILES = "IMPORTING_FILES"
    const val CREATING_MODEL = "CREATING_MODEL"
    const val DOCUMENT_REFS = "DOCUMENT_REFS"
    const val DONE = "DONE"

    const val STATUS_QUEUED = "QUEUED"
    const val STATUS_RUNNING = "RUNNING"
    const val STATUS_SUCCEEDED = "SUCCEEDED"
    const val STATUS_FAILED = "FAILED"
}

fun interface PackageImportProgressListener {
    fun onProgress(stage: String, progress: Int, message: String?)
}

data class PackageImportJobSnapshot(
    val ownerId: UUID,
    val tempPath: String,
    val overridesJson: String? = null
)
