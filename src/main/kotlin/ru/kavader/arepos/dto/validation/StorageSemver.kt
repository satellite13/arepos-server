package ru.kavader.arepos.dto.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import ru.kavader.arepos.util.VersionUtils
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [StorageSemverValidator::class])
annotation class StorageSemver(
    val message: String = "must be a semantic version without build metadata",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class StorageSemverValidator : ConstraintValidator<StorageSemver, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || VersionUtils.isValidStorageSemver(value)
}
