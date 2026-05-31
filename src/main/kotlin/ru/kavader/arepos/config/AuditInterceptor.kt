package ru.kavader.arepos.config

import org.hibernate.event.spi.*
import org.hibernate.engine.spi.SessionImplementor
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
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
         * Получает идентификатор текущего пользователя из ThreadLocal или HTTP-заголовка.
         */
        private fun getCurrentUserId(): UUID? {
            return threadLocalUserId.get() ?: try {
                SecurityContextHolder.getContext().authentication?.principal as? UUID
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun setSessionVariable(session: SessionImplementor, userId: UUID?) {
        if (userId != null) {
            try {
                session.doWork { connection ->
                    val statement = connection.createStatement()
                    statement.execute("SET LOCAL app.current_user_id = '$userId'")
                    statement.close()
                }
            } catch (_: Exception) {
                // Игнорируем ошибки установки переменной
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

