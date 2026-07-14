package ru.kavader.arepos.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.hibernate.event.spi.EventSource
import org.hibernate.event.spi.PreInsertEvent
import org.hibernate.jdbc.Work
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AuditInterceptorTest {

    @Test
    fun `logs warning and does not interrupt insert when audit session setup fails`() {
        val interceptor = AuditInterceptor()
        val event = mock(PreInsertEvent::class.java)
        val session = mock(EventSource::class.java)
        val connection = mock(Connection::class.java)
        val logger = LoggerFactory.getLogger(AuditInterceptor::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        AuditInterceptor.setCurrentUserId(UUID.randomUUID())

        try {
            `when`(event.session).thenReturn(session)
            doAnswer { invocation ->
                invocation.getArgument<Work>(0).execute(connection)
                null
            }.`when`(session).doWork(any(Work::class.java))
            `when`(connection.createStatement()).thenThrow(SQLException("database unavailable"))

            assertFalse(interceptor.onPreInsert(event))

            assertEquals(1, appender.list.size)
            assertEquals(Level.WARN, appender.list.single().level)
            assertEquals(
                "Unable to set audit session variable",
                appender.list.single().formattedMessage
            )
        } finally {
            AuditInterceptor.clearCurrentUserId()
            logger.detachAppender(appender)
            appender.stop()
        }
    }
}
