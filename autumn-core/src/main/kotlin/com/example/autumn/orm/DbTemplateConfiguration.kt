package com.example.autumn.orm

import com.example.autumn.annotation.Autowired
import com.example.autumn.annotation.Bean
import com.example.autumn.annotation.Configuration
import com.example.autumn.annotation.Value
import com.example.autumn.jdbc.JdbcTemplate

@Configuration
class DbTemplateConfiguration {
    @Bean
    fun dbTemplate(
        @Autowired jdbcTemplate: JdbcTemplate,
        @Value("\${autumn.db-template.entity-package-path:}") entityPackagePath: String,
    ): DbTemplate {
        return DbTemplate(jdbcTemplate, entityPackagePath)
    }
}