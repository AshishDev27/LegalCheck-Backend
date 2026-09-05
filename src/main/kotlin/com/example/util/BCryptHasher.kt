package com.example.util

import at.favre.lib.crypto.bcrypt.BCrypt

object BCryptHasher {
    private val hasher = BCrypt.withDefaults()
    private val verifier = BCrypt.verifyer()

    fun hashPassword(password: String): String {
        return hasher.hashToString(12, password.toCharArray())
    }

    fun checkPassword(password: String, hashed: String): Boolean {
        return verifier.verify(password.toCharArray(), hashed).verified
    }
}
