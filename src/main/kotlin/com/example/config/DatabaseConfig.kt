package com.example.config

import io.github.cdimascio.dotenv.dotenv

object DatabaseConfig {
    private val dotenv = dotenv {
        filename = "JJK.env"
        ignoreIfMalformed = true
        ignoreIfMissing = true
    }

    private val host = System.getenv("DB_HOST") ?: dotenv["DB_HOST"] ?: "localhost"

    private val port = System.getenv("DB_PORT") ?: dotenv["DB_PORT"] ?: "3306"

    private val name = System.getenv("DB_NAME") ?: dotenv["DB_NAME"] ?: "legalcheck"

    val user = System.getenv("DB_USER") ?: dotenv["DB_USER"] ?: "root"

    val password = System.getenv("DB_PASSWORD") ?: dotenv["DB_PASSWORD"] ?: ""

    val url =
        "jdbc:mysql://$host:$port/$name" +
                "?useSSL=false" +
                "&allowPublicKeyRetrieval=true" +
                "&serverTimezone=UTC" +
                "&createDatabaseIfNotExist=true"
}