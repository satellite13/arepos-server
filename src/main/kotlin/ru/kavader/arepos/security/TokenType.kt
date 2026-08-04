package ru.kavader.arepos.security

enum class TokenType(val claimValue: String) {
    ACCESS("access"),
    REFRESH("refresh"),
    MCP_ACCESS("mcp_access");

    companion object {
        fun fromClaimValue(value: String): TokenType = entries.firstOrNull { it.claimValue == value }
            ?: throw IllegalArgumentException("Unknown token type: $value")
    }
}
