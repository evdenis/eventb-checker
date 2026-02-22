package com.eventb.checker.validation

enum class ValidationSeverity { ERROR, WARNING, INFO }

data class ValidationError(
    val filePath: String,
    val severity: ValidationSeverity,
    val message: String,
    val element: String? = null,
    val formula: String? = null
)

data class ValidationSummary(
    val machineCount: Int,
    val contextCount: Int,
    val formulaCount: Int,
    val errorCount: Int,
    val warningCount: Int,
    val infoCount: Int = 0
)

data class ValidationResult(
    val errors: List<ValidationError>,
    val summary: ValidationSummary
) {
    val isValid: Boolean get() = errors.none { it.severity == ValidationSeverity.ERROR }
}
