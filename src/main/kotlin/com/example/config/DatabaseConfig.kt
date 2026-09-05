package com.example.config

import io.github.cdimascio.dotenv.dotenv

object DatabaseConfig {
    private val dotenv = dotenv {
        filename = "JJK.env"
        ignoreIfMalformed = true
        ignoreIfMissing = true
    }

    private val host = dotenv["DB_HOST"] ?: "localhost"
    private val port = dotenv["DB_PORT"] ?: "3306"
    private val name = dotenv["DB_NAME"] ?: "legalcheck" // Matching JJK.env default
    
    val user = dotenv["DB_USER"] ?: "root"
    val password = dotenv["DB_PASSWORD"] ?: ""
    val url = "jdbc:mysql://$host:$port/$name?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true"
}
