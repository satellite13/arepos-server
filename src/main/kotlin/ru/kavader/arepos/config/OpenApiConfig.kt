package ru.kavader.arepos.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig(
    private val buildProperties: BuildProperties
) {

    @Bean
    fun openAPI(): OpenAPI {
        val bearerAuth = "bearerAuth"
        return OpenAPI()
            .info(
                Info()
                    .title("Arepos API")
                    .version(buildProperties.version)
                    .description(
                        """
                        REST API для управления моделями, нотациями, типами узлов/связей и связанными сущностями.

                        Аутентификация:
                        - Браузер / wArchi: httpOnly cookies `warchi_access` + `warchi_refresh` после login/register/refresh;
                          для mutating-запросов нужен CSRF-заголовок `X-CSRF-Token` (значение cookie `warchi_csrf`).
                        - API-клиенты / тесты: `Authorization: Bearer <accessToken>` (токен также возвращается в теле auth-ответов).

                        См. также `docs/api-collaboration.md` (diagram-locks, batch-save) и `AGENTS.md`.
                        """.trimIndent()
                    )
            )
            .addSecurityItem(SecurityRequirement().addList(bearerAuth))
            .components(
                Components()
                    .addSecuritySchemes(
                        bearerAuth,
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description(
                                "JWT access token (тело auth-ответа или cookie warchi_access). " +
                                    "Для cookie-сессии браузера дополнительно нужен X-CSRF-Token на mutating-запросах."
                            )
                    )
            )
    }
}
