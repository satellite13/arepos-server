package ru.kavader.arepos.service.modelpackage

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.modelpackage.PackageImportConflictDto
import ru.kavader.arepos.dto.modelpackage.PackageImportErrorCodes

class PackageImportConflictException(
    val code: String,
    message: String,
    val conflict: PackageImportConflictDto
) : ResponseStatusException(HttpStatus.CONFLICT, message) {
    companion object {
        fun modelExists(name: String, version: String, suggestedVersion: String?): PackageImportConflictException =
            PackageImportConflictException(
                code = PackageImportErrorCodes.MODEL_EXISTS,
                message = "Model with name '$name' and version '$version' already exists",
                conflict = PackageImportConflictDto(
                    entity = "model",
                    name = name,
                    version = version,
                    suggestedVersion = suggestedVersion
                )
            )

        fun notationExistsForbidden(name: String, version: String): PackageImportConflictException =
            PackageImportConflictException(
                code = PackageImportErrorCodes.NOTATION_EXISTS_FORBIDDEN,
                message = "Notation with name '$name' and version '$version' already exists but is not accessible",
                conflict = PackageImportConflictDto(
                    entity = "notation",
                    name = name,
                    version = version
                )
            )

        fun notationIncompatible(
            name: String,
            version: String,
            details: List<String>
        ): PackageImportConflictException =
            PackageImportConflictException(
                code = PackageImportErrorCodes.NOTATION_INCOMPATIBLE,
                message = "Existing notation '$name' v$version is incompatible with the package",
                conflict = PackageImportConflictDto(
                    entity = "notation",
                    name = name,
                    version = version,
                    details = details
                )
            )
    }
}
