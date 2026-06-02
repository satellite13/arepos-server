package ru.kavader.arepos.controller

fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

fun String?.normalizedNameOrEmpty(): String = this?.trim().orEmpty()

fun parseCommaSeparatedTags(raw: String?): List<String> =
    raw
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.distinct()
        ?: emptyList()

fun List<String>.toTagsJsonArrayOrNull(): String? =
    if (isEmpty()) null else joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "\\\"")}\"" }
