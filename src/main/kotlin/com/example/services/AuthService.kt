package com.example.services

import com.example.dtos.*
import com.example.repositories.UserRepository
import com.example.util.BCryptHasher
import com.example.util.JwtProvider

object AuthService {
    fun register(request: RegisterRequest): AuthResponse {
        if (UserRepository.findByInspectorId(request.inspectorId) != null) {
            throw IllegalArgumentException("Inspector already registered")
        }

        val hashedPassword = BCryptHasher.hashPassword(request.passcode)
        val userId = UserRepository.create(
            fullName = request.fullName,
            inspectorId = request.inspectorId,
            division = request.division,
            email = request.email,
            passcodeHash = hashedPassword
        )

        val token = JwtProvider.createToken(userId.toString())
        val user = UserRepository.findById(userId)!!

        return AuthResponse(
            token = token,
            user = UserResponse(
                id = user.id,
                fullName = user.fullName,
                inspectorId = user.inspectorId,
                division = user.division,
                email = user.email,
                createdAt = user.createdAt
            )
        )
    }

    fun login(request: LoginRequest): AuthResponse {
        val user = UserRepository.findByInspectorId(request.inspectorId)
            ?: throw IllegalArgumentException("Invalid Inspector ID or passcode")

        if (!BCryptHasher.checkPassword(request.passcode, user.passcode)) {
            throw IllegalArgumentException("Invalid Inspector ID or passcode")
        }

        val token = JwtProvider.createToken(user.id.toString())

        return AuthResponse(
            token = token,
            user = UserResponse(
                id = user.id,
                fullName = user.fullName,
                inspectorId = user.inspectorId,
                division = user.division,
                email = user.email,
                createdAt = user.createdAt
            )
        )
    }

    fun getMe(userId: Int): UserResponse {
        val user = UserRepository.findById(userId)
            ?: throw IllegalArgumentException("User not found")

        return UserResponse(
            id = user.id,
            fullName = user.fullName,
            inspectorId = user.inspectorId,
            division = user.division,
            email = user.email,
            createdAt = user.createdAt
        )
    }
}
