package ru.kavader.arepos.config

import org.hibernate.HibernateException
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.event.spi.*
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.sql.SQLException
import java.util.*

/**
 * Перехватчик событий Hibernate для установки переменной сессии PostgreSQL `app.current_user_id`
 * перед выполнением операций INSERT, UPDATE, DELETE.
 *
 * Эта переменная используется триггером `audit_trigger` для записи идентификатора пользователя
 * в таблицу `audit_log`.
 *
 * Идентификатор пользователя устанавливается через `AuditInterceptor.setCurrentUserId(userId)`
 * из `JwtAuthenticationFilter` после успешной валидации JWT-токена,
 * либо извлекается из `SecurityContextHolder`.
 */
@Component
class AuditInterceptor : PreInsertEventListener, PreUpdateEventListener, PreDeleteEventListener {

    companion object {
        private val threadLocalUserId = ThreadLocal<UUID?>()
        private val logger = LoggerFactory.getLogger(AuditInterceptor::class.java)

        /**
         * Устанавливает идентификатор текущего пользователя для текущего потока.
         * Используется для программной установки userId вне HTTP-запросов.
         */
        fun setCurrentUserId(userId: UUID?) {
            threadLocalUserId.set(userId)
        }

        /**
         * Очищает идентификатор текущего пользователя для текущего потока.
         */
        fun clearCurrentUserId() {
            threadLocalUserId.remove()
        }

        /**
         * Получает идентификатор текущего пользователя.
         * Приоритет у SecurityContext, чтобы избежать утечек из stale ThreadLocal.
         */
        private fun getCurrentUserId(): UUID? {
            return try {
                SecurityContextHolder.getContext().authentication?.principal as? UUID
            } catch (_: ClassCastException) {
                logger.warn("Unable to read audit principal from security context")
                null
            } ?: threadLocalUserId.get()
        }
    }

    private fun setSessionVariable(session: SessionImplementor, userId: UUID?) {
        if (userId != null) {
            try {
                session.doWork { connection ->
                    try {
                        connection.createStatement().use { statement ->
                            statement.execute("SET LOCAL app.current_user_id = '$userId'")
                        }
                    } catch (_: SQLException) {
                        logger.warn("Unable to set audit session variable")
                    }
                }
            } catch (_: HibernateException) {
                logger.warn("Unable to set audit session variable")
            }
        }
    }

    override fun onPreInsert(event: PreInsertEvent): Boolean {
        val session = event.session as? SessionImplementor
        session?.let { setSessionVariable(it, getCurrentUserId()) }
        return false
    }

    override fun onPreUpdate(event: PreUpdateEvent): Boolean {
        val session = event.session as? SessionImplementor
        session?.let { setSessionVariable(it, getCurrentUserId()) }
        return false
    }

    override fun onPreDelete(event: PreDeleteEvent): Boolean {
        val session = event.session as? SessionImplementor
        session?.let { setSessionVariable(it, getCurrentUserId()) }
        return false
    }
}

