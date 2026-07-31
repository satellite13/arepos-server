package ru.kavader.arepos.service

import io.minio.errors.ErrorResponseException
import io.minio.errors.MinioException

internal fun formatMinioFailure(ex: Throwable): String {
    val parts = mutableListOf(ex.javaClass.simpleName)
    when (ex) {
        is ErrorResponseException -> {
            val err = ex.errorResponse()
            parts += "code=${err.code()}"
            parts += "message=${err.message()}"
            if (!err.requestId().isNullOrBlank()) parts += "requestId=${err.requestId()}"
            if (!err.resource().isNullOrBlank()) parts += "resource=${err.resource()}"
            parts += "http=${ex.response().code}"
            runCatching {
                parts += "url=${ex.response().request.url}"
            }
        }
        is MinioException -> {
            if (!ex.message.isNullOrBlank()) parts += "message=${ex.message}"
            if (!ex.httpTrace().isNullOrBlank()) parts += "trace=${ex.httpTrace()}"
        }
        else -> {
            if (!ex.message.isNullOrBlank()) parts += "message=${ex.message}"
            ex.cause?.let { parts += "cause=${formatMinioFailure(it)}" }
        }
    }
    return parts.joinToString(", ")
}
