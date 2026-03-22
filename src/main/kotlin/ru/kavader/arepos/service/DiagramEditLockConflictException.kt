package ru.kavader.arepos.service

import ru.kavader.arepos.dto.DiagramLockStatusResponse

class DiagramEditLockConflictException(
    val body: DiagramLockStatusResponse
) : RuntimeException("Diagram edit lock held by another user")
