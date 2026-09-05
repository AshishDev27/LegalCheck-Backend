package com.example

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer

fun main(args: Array<String>) {
    try {
        embeddedServer(
            factory = io.ktor.server.netty.Netty,
            port = 8080,
            host = "0.0.0.0",
            module = Application::rootModule
        ).start(wait = true)
    } catch (e: Exception) {
        e.printStackTrace()
        System.exit(1)
    }
}
