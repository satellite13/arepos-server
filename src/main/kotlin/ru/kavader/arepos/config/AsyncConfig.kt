package ru.kavader.arepos.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableAsync
class AsyncConfig {
    @Bean(name = ["packageImportExecutor"])
    fun packageImportExecutor(): AsyncTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 1
        executor.maxPoolSize = 2
        executor.queueCapacity = 20
        executor.setThreadNamePrefix("package-import-")
        executor.initialize()
        return executor
    }
}
