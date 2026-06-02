package ru.kavader.arepos.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(AreposFilesProperties::class)
class AreposFilesConfig
