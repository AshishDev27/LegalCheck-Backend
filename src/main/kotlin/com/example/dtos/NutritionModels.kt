package com.example.dtos

import kotlinx.serialization.Serializable

@Serializable
data class NutrientValue(
    val per100g: Double? = null,
    val perServing: Double? = null,
    val percentRda: Double? = null,
    val unit: String? = null,
    val evidence: String? = null
)

@Serializable
data class NutritionFacts(
    val servingSize: String? = null,
    val servingSizeUnit: String? = null,
    val numberOfServings: Double? = null,
    val basis: String? = null,
    val nutrients: Map<String, NutrientValue> = emptyMap(),
    val evidence: String? = null,
    val confidence: Float = 0f
)
