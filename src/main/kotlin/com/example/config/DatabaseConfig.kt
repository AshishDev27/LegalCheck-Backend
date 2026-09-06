package com.example.config

object DatabaseConfig {

    private val host = System.getenv("DB_HOST")
        ?: error("DB_HOST is missing")

    private val port = System.getenv("DB_PORT")
        ?: "3306"

    private val name = System.getenv("DB_NAME")
        ?: error("DB_NAME is missing")

    val user = System.getenv("DB_USER")
        ?: error("DB_USER is missing")

    val password = System.getenv("DB_PASSWORD")
        ?: error("DB_PASSWORD is missing")

    val url =
        "jdbc:mysql://$host:$port/$name" +
                "?useSSL=false" +
                "&allowPublicKeyRetrieval=true" +
                "&serverTimezone=UTC" +
                "&createDatabaseIfNotExist=true"
}