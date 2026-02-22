package com.eventb.checker.xml

import com.eventb.checker.model.Context
import com.eventb.checker.model.Machine
import com.eventb.checker.validation.ValidationError
import com.eventb.checker.validation.ValidationSeverity
import org.w3c.dom.Document
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

class RodinXmlParser {

    private val machineParser = MachineXmlParser()
    private val contextParser = ContextXmlParser()

    data class MachineParseResult(val machine: Machine?, val errors: List<ValidationError>)
    data class ContextParseResult(val context: Context?, val errors: List<ValidationError>)

    fun parseMachine(input: InputStream, filePath: String): MachineParseResult {
        val doc = parseXml(input, filePath)
            ?: return MachineParseResult(null, listOf(xmlParseError(filePath)))
        val result = machineParser.parse(doc, filePath)
        return MachineParseResult(result.machine, result.errors)
    }

    fun parseContext(input: InputStream, filePath: String): ContextParseResult {
        val doc = parseXml(input, filePath)
            ?: return ContextParseResult(null, listOf(xmlParseError(filePath)))
        val result = contextParser.parse(doc, filePath)
        return ContextParseResult(result.context, result.errors)
    }

    private fun parseXml(input: InputStream, filePath: String): Document? {
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            val builder = factory.newDocumentBuilder()
            builder.parse(input)
        } catch (e: Exception) {
            null
        }
    }

    private fun xmlParseError(filePath: String) = ValidationError(
        filePath = filePath,
        severity = ValidationSeverity.ERROR,
        message = "Failed to parse XML"
    )
}
