package com.example.dtos

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val fullName: String,
    val inspectorId: String,
    val division: String,
    val email: String,
    val passcode: String
)

@Serializable
data class LoginRequest(
    val inspectorId: String,
    val passcode: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserResponse
)

@Serializable
data class UserResponse(
    val id: Int,
    val fullName: String,
    val inspectorId: String,
    val division: String,
    val email: String,
    val createdAt: String
)
