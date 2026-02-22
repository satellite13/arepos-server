package ru.kavader.arepos.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

fun <T> List<T>.toPage(pageable: Pageable): Page<T> {
    if (isEmpty()) {
        return Page.empty(pageable)
    }
    val start = pageable.offset.toInt().coerceAtMost(size)
    val end = (start + pageable.pageSize).coerceAtMost(size)
    return PageImpl(subList(start, end), pageable, size.toLong())
}
