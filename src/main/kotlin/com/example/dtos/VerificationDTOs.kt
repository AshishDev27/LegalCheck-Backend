package com.example.dtos

import kotlinx.serialization.Serializable

@Serializable
data class VerificationResult(
    val inspectionId: Int,
    val productName: String,
    val inspectionDate: String,
    val complianceScore: Int,
    val complianceStatus: String,
    val verificationStatus: String, // VERIFIED, INVALID, etc.
    val message: String? = null
)
