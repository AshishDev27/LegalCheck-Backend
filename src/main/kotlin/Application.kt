package com.example

import com.example.config.DatabaseFactory
import com.example.plugins.configureSecurity
import com.example.plugins.configureStatusPages
import io.ktor.server.application.Application

fun Application.rootModule() {
    DatabaseFactory.init()
    configureSerialization()
    configureSecurity()
    configureStatusPages()
    configureRouting()
}
