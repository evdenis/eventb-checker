package com.eventb.checker.report

import com.eventb.checker.validation.ValidationResult
import com.eventb.checker.validation.ValidationRules
import com.eventb.checker.validation.ValidationSeverity
import org.json.JSONArray
import org.json.JSONObject

class SarifReportFormatter : ReportFormatter {

    companion object {
        private const val SARIF_SCHEMA = "https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json"
        private const val SARIF_VERSION = "2.1.0"
        private const val TOOL_NAME = "eventb-checker"
        private const val TOOL_INFO_URI = "https://github.com/evdenis/eventb-checker"

        private val TOOL_VERSION: String by lazy {
            SarifReportFormatter::class.java.`package`?.implementationVersion ?: "dev"
        }
    }

    override fun format(result: ValidationResult): String {
        val referencedRuleIds = result.errors.mapNotNull { it.ruleId }.toSet()

        val rules = JSONArray()
        for (rule in ValidationRules.ALL) {
            if (rule.id in referencedRuleIds) {
                rules.put(
                    JSONObject()
                        .put("id", rule.id)
                        .put("shortDescription", JSONObject().put("text", rule.shortDescription))
                        .put("fullDescription", JSONObject().put("text", rule.fullDescription)),
                )
            }
        }

        val driver = JSONObject()
            .put("name", TOOL_NAME)
            .put("version", TOOL_VERSION)
            .put("informationUri", TOOL_INFO_URI)
            .put("rules", rules)

        val results = JSONArray()
        for (error in result.errors) {
            val resultObj = JSONObject()
                .put("message", JSONObject().put("text", error.message))
                .put("level", mapSeverity(error.severity))

            error.ruleId?.let { resultObj.put("ruleId", it) }

            val locations = JSONArray()
            val location = JSONObject()

            location.put(
                "physicalLocation",
                JSONObject().put(
                    "artifactLocation",
                    JSONObject().put("uri", error.filePath),
                ),
            )

            error.element?.let {
                location.put(
                    "logicalLocations",
                    JSONArray().put(
                        JSONObject()
                            .put("name", it)
                            .put("kind", "element"),
                    ),
                )
            }

            locations.put(location)
            resultObj.put("locations", locations)

            if (error.formula != null) {
                resultObj.put("properties", JSONObject().put("formula", error.formula))
            }

            results.put(resultObj)
        }

        val run = JSONObject()
            .put("tool", JSONObject().put("driver", driver))
            .put("results", results)

        return JSONObject()
            .put("\$schema", SARIF_SCHEMA)
            .put("version", SARIF_VERSION)
            .put("runs", JSONArray().put(run))
            .toString()
    }

    private fun mapSeverity(severity: ValidationSeverity): String = when (severity) {
        ValidationSeverity.ERROR -> "error"
        ValidationSeverity.WARNING -> "warning"
        ValidationSeverity.INFO -> "note"
    }
}
