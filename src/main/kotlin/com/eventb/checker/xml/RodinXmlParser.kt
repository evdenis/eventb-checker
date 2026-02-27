package com.eventb.checker.xml

import com.eventb.checker.model.Context
import com.eventb.checker.model.Machine
import com.eventb.checker.validation.ValidationError
import com.eventb.checker.validation.ValidationRules
import com.eventb.checker.validation.ValidationSeverity
import java.io.InputStream

class RodinXmlParser {

    private val machineParser = MachineXmlParser()
    private val contextParser = ContextXmlParser()

    data class MachineParseResult(val machine: Machine?, val errors: List<ValidationError>)
    data class ContextParseResult(val context: Context?, val errors: List<ValidationError>)

    fun parseMachine(input: InputStream, filePath: String): MachineParseResult {
        val (doc, errorDetail) = parseXmlDocument(input)
        if (doc == null) return MachineParseResult(null, listOf(xmlParseError(filePath, errorDetail)))
        val result = machineParser.parse(doc, filePath)
        return MachineParseResult(result.machine, result.errors)
    }

    fun parseContext(input: InputStream, filePath: String): ContextParseResult {
        val (doc, errorDetail) = parseXmlDocument(input)
        if (doc == null) return ContextParseResult(null, listOf(xmlParseError(filePath, errorDetail)))
        val result = contextParser.parse(doc, filePath)
        return ContextParseResult(result.context, result.errors)
    }

    private fun xmlParseError(filePath: String, detail: String?) = ValidationError(
        filePath = filePath,
        severity = ValidationSeverity.ERROR,
        message = if (detail != null) "Failed to parse XML: $detail" else "Failed to parse XML",
        ruleId = ValidationRules.XML_PARSE_ERROR.id,
    )
}
