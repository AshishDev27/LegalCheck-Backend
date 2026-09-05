package com.example.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.github.cdimascio.dotenv.dotenv
import java.util.*

object JwtProvider {
    private val dotenv = dotenv {
        filename = "JJK.env"
        ignoreIfMalformed = true
        ignoreIfMissing = true
    }

    private val secret = dotenv["JWT_SECRET"] ?: "default_secret"
    private val issuer = "com.example"
    private val audience = "com.example.audience"
    private val validityInMs = 36_000_000 // 10 hours

    val algorithm: Algorithm = Algorithm.HMAC256(secret)

    fun createToken(userId: String): String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", userId)
            .withExpiresAt(Date(System.currentTimeMillis() + validityInMs))
            .sign(algorithm)
    }

    fun getVerifier() = JWT.require(algorithm)
        .withAudience(audience)
        .withIssuer(issuer)
        .build()
}
