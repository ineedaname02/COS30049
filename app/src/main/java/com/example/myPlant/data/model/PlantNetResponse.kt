package com.example.myPlant.data.model

data class PlantNetResponse(
    val query: Query?,
    val language: String?,
    val preferedReferential: String?,
    val bestMatch: String?,
    val results: List<Result>?,
    val remainingIdentificationRequests: Int?,
    val version: String?,
    val predictedOrgans: List<PredictedOrgan>?,
    val otherResults: OtherResults?
)

data class Query(
    val project: String?,
    val images: List<String>?,
    val organs: List<String>?,
    val includeRelatedImages: Boolean?,
    val noReject: Boolean?,
    val type: String?
)

data class Result(
    val score: Double?,
    val species: Species?,
    val images: List<ResultImage>?,
    val gbif: Gbif?,
    val powo: Powo?,
    val iucn: Iucn?
)

data class Species(
    val scientificNameWithoutAuthor: String?,
    val scientificNameAuthorship: String?,
    val scientificName: String?,
    val genus: Genus?,
    val family: Family?,
    val commonNames: List<String>?
)

data class Genus(
    val scientificNameWithoutAuthor: String?,
    val scientificNameAuthorship: String?,
    val scientificName: String?
)

data class Family(
    val scientificNameWithoutAuthor: String?,
    val scientificNameAuthorship: String?,
    val scientificName: String?,
    val commonNames: List<String>?
)

data class ResultImage(
    val organ: String?,
    val author: String?,
    val license: String?,
    val date: ImageDate?,
    val citation: String?,
    val url: ImageUrl?
)

data class ImageDate(
    val timestamp: Long?,
    val string: String?
)

data class ImageUrl(
    val o: String?,
    val m: String?,
    val s: String?
)

data class Gbif(val id: Int?)
data class Powo(val id: String?)
data class Iucn(val id: String?, val category: String?)

data class PredictedOrgan(
    val image: String?,
    val filename: String?,
    val organ: String?,
    val score: Double?
)

data class OtherResults(
    val genus: List<OtherGenus>?,
    val family: List<OtherFamily>?
)

data class OtherGenus(
    val score: Double?,
    val genus: Genus?,
    val gbif: Gbif?,
    val images: List<ResultImage>?
)

data class OtherFamily(
    val score: Double?,
    val family: Family?,
    val gbif: Gbif?,
    val images: List<ResultImage>?
)
