package ru.kavader.arepos.config

import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManagerFactory
import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.event.spi.EventType
import org.hibernate.internal.SessionFactoryImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration

@Configuration
class JpaConfig {

    @Autowired
    lateinit var auditInterceptor: AuditInterceptor

    @Autowired
    lateinit var entityManagerFactory: EntityManagerFactory

    @PostConstruct
    fun registerEventListeners() {
        val sessionFactory = entityManagerFactory.unwrap(SessionFactoryImpl::class.java)
        val registry = sessionFactory.serviceRegistry.getService(EventListenerRegistry::class.java)

        registry?.let {
            it.appendListeners(EventType.PRE_INSERT, auditInterceptor)
            it.appendListeners(EventType.PRE_UPDATE, auditInterceptor)
            it.appendListeners(EventType.PRE_DELETE, auditInterceptor)
        }
    }
}

