package com.example.controllers

import com.example.dtos.LoginRequest
import com.example.dtos.RegisterRequest
import com.example.services.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*

object AuthController {
    suspend fun register(call: ApplicationCall) {
        val request = call.receive<RegisterRequest>()
        val response = AuthService.register(request)
        call.respond(HttpStatusCode.Created, response)
    }

    suspend fun login(call: ApplicationCall) {
        val request = call.receive<LoginRequest>()
        val response = AuthService.login(request)
        call.respond(HttpStatusCode.OK, response)
    }

    suspend fun getMe(call: ApplicationCall) {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.payload?.getClaim("userId")?.asString()?.toIntOrNull()
            ?: throw IllegalArgumentException("Invalid token")
        
        val response = AuthService.getMe(userId)
        call.respond(HttpStatusCode.OK, response)
    }
}
