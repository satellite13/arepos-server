package ru.kavader.arepos.config

import org.hibernate.event.spi.*
import org.hibernate.engine.spi.SessionImplementor
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.*

/**
 * Перехватчик событий Hibernate для установки переменной сессии PostgreSQL `app.current_user_id`
 * перед выполнением операций INSERT, UPDATE, DELETE.
 * 
 * Эта переменная используется триггером `audit_trigger` для записи идентификатора пользователя
 * в таблицу `audit_log`.
 * 
 * Идентификатор пользователя может быть установлен двумя способами:
 * 1. Через заголовок HTTP-запроса `X-User-Id`
 * 2. Программно через `AuditInterceptor.setCurrentUserId(userId)`
 * 
 * Пример использования в контроллере:
 * ```
 * @PostMapping
 * fun createModel(@RequestBody request: ModelRequest, @RequestHeader("X-User-Id") userId: UUID) {
 *     // userId автоматически будет использован триггером audit_trigger
 *     return modelsRepository.save(...)
 * }
 * ```
 */
@Component
class AuditInterceptor : PreInsertEventListener, PreUpdateEventListener, PreDeleteEventListener {

    companion object {
        private val USER_ID_HEADER = "X-User-Id"
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
                val requestAttributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
                requestAttributes?.request?.getHeader(USER_ID_HEADER)?.let { UUID.fromString(it) }
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

