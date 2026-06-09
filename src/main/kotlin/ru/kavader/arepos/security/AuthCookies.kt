package ru.kavader.arepos.security

object AuthCookies {
    const val ACCESS = "warchi_access"
    const val REFRESH = "warchi_refresh"
    const val CSRF = "warchi_csrf"
    const val CSRF_HEADER = "X-CSRF-Token"
    const val REFRESH_PATH = "/api/v1/auth/refresh"
}
