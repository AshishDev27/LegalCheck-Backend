package com.example.dtos

import kotlinx.serialization.Serializable

@Serializable
data class InspectionCreateRequest(
    val productName: String
)

@Serializable
data class InspectionResponse(
    val id: Int,
    val productName: String,
    val status: String,
    val createdAt: String,
    val complianceStatus: String? = null,
    val images: List<InspectionImageResponse> = emptyList()
)

@Serializable
data class InspectionImageResponse(
    val id: Int,
    val imageUrl: String,
    val type: String
)

/**
 * The single structured representation of a scanned product, built by combining the FRONT and
 * BACK panels of one physical package.
 *
 * Every declaration field is nullable on purpose: `null` means "not found / not readable" and is
 * never substituted with a placeholder. Nothing in the pipeline is allowed to invent a value.
 */
@Serializable
data class ProductDeclarationDto(
    val commodityCategory: String = "GENERAL",

    // Identity - usually printed on the FRONT panel
    val productName: String? = null,
    val brand: String? = null,
    val variant: String? = null,
    val commodityName: String? = null,

    // Responsible entities - usually printed on the BACK panel
    val manufacturerName: String? = null,
    val manufacturerAddress: String? = null,
    val packerName: String? = null,
    val packerAddress: String? = null,
    val importerName: String? = null,
    val importerAddress: String? = null,
    val countryOfOrigin: String? = null,

    // Quantity and price
    val netQuantity: String? = null,
    val netQuantityUnit: String? = null,
    val mrp: String? = null,
    val mrpInclusiveOfTaxes: Boolean = false,
    val unitSalePrice: String? = null,
    val dimensions: String? = null,

    // Traceability
    val batchNumber: String? = null,
    val lotNumber: String? = null,
    val licenseNumber: String? = null,

    // Dates
    val manufacturingDate: String? = null,
    val packingDate: String? = null,
    val bestBefore: String? = null,
    val expiryDate: String? = null,
    val useBy: String? = null,

    // Product content
    val ingredients: String? = null,
    val nutrition: NutritionFacts? = null,
    val allergens: List<String> = emptyList(),
    val usageInstructions: String? = null,
    val warnings: String? = null,

    // Consumer care
    val consumerCareName: String? = null,
    val consumerCarePhone: String? = null,
    val consumerCareEmail: String? = null,

    // Supplement specific
    val servingSize: String? = null,
    val servingSizeUnit: String? = null,
    val numberOfServings: Double? = null,

    val otherDeclarations: List<String> = emptyList(),

    // Provenance / debugging. Kept so an inspector can see where a value came from and why a
    // field was left empty, instead of having to trust an opaque extraction.
    val fieldSources: Map<String, String> = emptyMap(),
    val fieldConfidence: Map<String, Float> = emptyMap(),
    val unreadableFields: List<String> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val rawFrontText: String? = null,
    val rawBackText: String? = null,
    val confidence: Float = 0f
)

@Serializable
data class ExtractionRequest(
    val frontOcrText: String? = null,
    val backOcrText: String? = null,
    val additionalOcrText: List<String> = emptyList()
)
