package ru.kavader.arepos.security

import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import ru.kavader.arepos.config.AreposAuthProperties
import ru.kavader.arepos.config.AreposSwaggerProperties

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(
    JwtProperties::class,
    AreposAuthProperties::class,
    AreposSwaggerProperties::class,
    OidcProperties::class
)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val csrfFilter: CsrfFilter,
    private val authProperties: AreposAuthProperties
) {
    companion object {
        private val log = LoggerFactory.getLogger(SecurityConfig::class.java)
    }

    /** Публичные пути (/, Swagger UI, api-docs) — без JWT, только если swagger включён. */
    @Bean
    @Order(0)
    @ConditionalOnProperty(prefix = "arepos.swagger", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun publicSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatchers { matchers ->
                matchers
                    .requestMatchers(
                        "/",
                        "/swagger-ui.html",
                        "/swagger-ui",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**"
                    )
            }
            .authorizeHttpRequests { auth -> auth.anyRequest().permitAll() }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .build()
    }

    /** API и остальные пути — JWT + авторизация. */
    @Bean
    @Order(1)
    fun apiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    .requestMatchers("/api/v1/auth/**").permitAll()
                    .requestMatchers("/actuator/health/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/system/version").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/diagrams/svg/public/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/feedback", "/api/v1/feedback/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/roadmap", "/api/v1/roadmap/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/tutorials", "/api/v1/tutorials/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/downloads").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint { request, response, _ ->
                    val hasAuthHeader = request.getHeader("Authorization")?.startsWith("Bearer ") == true
                    log.warn(
                        "Security entrypoint 401: path={} {}, hasAuthorizationHeader={}",
                        request.method,
                        request.requestURI,
                        hasAuthHeader
                    )
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
                }
            }
            .addFilterBefore(csrfFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()
        val origins = authProperties.corsAllowedOrigins
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (origins.isEmpty()) {
            return source
        }
        val config = CorsConfiguration()
        config.allowCredentials = true
        config.allowedOrigins = origins
        config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")
        config.allowedHeaders = listOf("*")
        config.exposedHeaders = listOf("Content-Disposition")
        source.registerCorsConfiguration("/api/**", config)
        return source
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
