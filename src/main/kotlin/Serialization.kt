package com.example

import io.ktor.server.application.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        // ignoreUnknownKeys is required: the Android client posts a declaration model that
        // may carry fields this build of the server does not know about yet. Without it the
        // whole /analyze request is rejected and the client silently falls back to a local stub.
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
        })
    }
}
