package com.phodal.shirecore.guard.model

import com.intellij.openapi.diagnostic.logger
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joni.Regex;

@Serializable
data class SecretPatterns(
    val patterns: List<SecretPatternItem>
)

@Serializable
data class SecretPatternItem(
    val pattern: SecretPatternDetail
)

@Serializable
class SecretPatternDetail(
    val name: String,
    val regex: String,
    val confidence: String
) {
//    @Contextual
//    private val regexPattern: Regex? = try {
//        Regex(regex)
//    } catch (e: Exception) {
//        logger<SecretPatternDetail>().error("Invalid regex pattern: $regex")
//        null
//    }
}
