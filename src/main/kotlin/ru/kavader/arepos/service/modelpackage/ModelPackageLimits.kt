package ru.kavader.arepos.service.modelpackage

object ModelPackageLimits {
    const val MAX_ZIP_BYTES: Long = 100L * 1024L * 1024L
    const val MAX_NOTATIONS = 50
    const val MAX_FILES = 500
    const val MAX_NODES = 50_000
    const val MAX_LINKS = 100_000
    const val MAX_DIAGRAMS = 5_000
    const val FORMAT = "warchi-model-package"
    const val VERSION = 1
}
