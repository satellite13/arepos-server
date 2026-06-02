package ru.kavader.arepos.dto.common

import org.springframework.data.domain.Page

data class ListResponse<T>(
    val items: List<T>,
    val total: Long,
    val page: Int,
    val size: Int
)

fun <T> Page<T>.toListResponse(): ListResponse<T> =
    ListResponse(
        items = content,
        total = totalElements,
        page = number,
        size = size
    )

fun <T> List<T>.toListResponse(page: Int = 0, size: Int = this.size): ListResponse<T> =
    ListResponse(
        items = this,
        total = this.size.toLong(),
        page = page,
        size = size
    )
