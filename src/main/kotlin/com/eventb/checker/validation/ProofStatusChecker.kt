package com.eventb.checker.validation

import com.eventb.checker.ModelContents
import com.eventb.checker.ModelEntry
import com.eventb.checker.model.ProofConfidence
import com.eventb.checker.model.ProofObligation
import com.eventb.checker.model.ProofStatusSummary
import com.eventb.checker.xml.XmlConstants
import com.eventb.checker.xml.childElements
import com.eventb.checker.xml.parseXmlDocument

data class ProofStatusResult(
    val obligations: List<ProofObligation>,
    val summary: ProofStatusSummary,
    val hasCompleteInfo: Boolean,
    val errors: List<ValidationError>,
)

class ProofStatusChecker {

    fun check(contents: ModelContents): ProofStatusResult {
        val errors = mutableListOf<ValidationError>()

        val prData = parseProofFiles(contents.proofFiles, errors)
        val poData = parsePoFiles(contents.proofObligationFiles, errors)
        val psData = parsePsFiles(contents.proofStatusFiles, errors)

        val obligations = buildObligations(prData, poData, psData)

        val summary = ProofStatusSummary(
            total = obligations.size,
            discharged = obligations.count { it.confidence == ProofConfidence.DISCHARGED },
            reviewed = obligations.count { it.confidence == ProofConfidence.REVIEWED },
            pending = obligations.count { it.confidence == ProofConfidence.PENDING },
            unattempted = obligations.count { it.confidence == ProofConfidence.UNATTEMPTED },
            broken = obligations.count { it.broken },
        )

        val undischarged = obligations.filter { it.confidence != ProofConfidence.DISCHARGED }
        for (po in undischarged) {
            errors.add(
                ValidationError(
                    filePath = po.component,
                    severity = ValidationSeverity.WARNING,
                    message = "Proof obligation not discharged: ${po.name} (${po.confidence.name.lowercase()})",
                    element = po.name,
                ),
            )
        }

        val brokenProofs = obligations.filter { it.broken }
        for (po in brokenProofs) {
            errors.add(
                ValidationError(
                    filePath = po.component,
                    severity = ValidationSeverity.WARNING,
                    message = "Broken proof: ${po.name}",
                    element = po.name,
                ),
            )
        }

        return ProofStatusResult(obligations, summary, poData.isNotEmpty(), errors)
    }

    private data class PrEntry(val component: String, val name: String, val confidence: Int?)

    private data class PoEntry(val component: String, val name: String, val description: String?)

    private data class PsEntry(val component: String, val name: String, val manual: Boolean, val broken: Boolean)

    private fun <T> parseXmlEntries(
        files: List<ModelEntry>,
        suffix: String,
        tag: String,
        errors: MutableList<ValidationError>,
        extract: (component: String, child: org.w3c.dom.Element) -> T?,
    ): List<T> {
        val result = mutableListOf<T>()
        for (entry in files) {
            val component = entry.path.substringAfterLast('/').removeSuffix(suffix)
            val doc = parseXml(entry, errors) ?: continue
            for (child in doc.documentElement.childElements()) {
                if (child.tagName == tag) {
                    extract(component, child)?.let { result.add(it) }
                }
            }
        }
        return result
    }

    private fun parseProofFiles(files: List<ModelEntry>, errors: MutableList<ValidationError>): List<PrEntry> =
        parseXmlEntries(files, XmlConstants.EXT_PROOF, XmlConstants.PR_PROOF, errors) { component, child ->
            val name = child.getAttribute(XmlConstants.ATTR_NAME).ifEmpty { return@parseXmlEntries null }
            PrEntry(component, name, child.getAttribute(XmlConstants.ATTR_CONFIDENCE).toIntOrNull())
        }

    private fun parsePoFiles(files: List<ModelEntry>, errors: MutableList<ValidationError>): List<PoEntry> =
        parseXmlEntries(files, XmlConstants.EXT_PROOF_OBLIGATIONS, XmlConstants.PO_SEQUENT, errors) { component, child ->
            val name = child.getAttribute(XmlConstants.ATTR_NAME).ifEmpty { return@parseXmlEntries null }
            PoEntry(component, name, child.getAttribute(XmlConstants.ATTR_PO_DESC).ifEmpty { null })
        }

    private fun parsePsFiles(files: List<ModelEntry>, errors: MutableList<ValidationError>): List<PsEntry> =
        parseXmlEntries(files, XmlConstants.EXT_PROOF_STATUS, XmlConstants.PS_STATUS, errors) { component, child ->
            val name = child.getAttribute(XmlConstants.ATTR_NAME).ifEmpty { return@parseXmlEntries null }
            PsEntry(
                component,
                name,
                child.getAttribute(XmlConstants.ATTR_PS_MANUAL) == "true",
                child.getAttribute(XmlConstants.ATTR_PS_BROKEN) == "true",
            )
        }

    private fun buildObligations(prData: List<PrEntry>, poData: List<PoEntry>, psData: List<PsEntry>): List<ProofObligation> {
        val prMap = prData.associateBy { "${it.component}/${it.name}" }
        val psMap = psData.associateBy { "${it.component}/${it.name}" }

        if (poData.isNotEmpty()) {
            return poData.map { po ->
                val key = "${po.component}/${po.name}"
                val pr = prMap[key]
                val ps = psMap[key]
                ProofObligation(
                    name = po.name,
                    component = po.component,
                    description = po.description,
                    confidence = classifyConfidence(pr?.confidence),
                    manual = ps?.manual ?: false,
                    broken = ps?.broken ?: false,
                )
            }
        }

        return prData.map { pr ->
            val key = "${pr.component}/${pr.name}"
            val ps = psMap[key]
            ProofObligation(
                name = pr.name,
                component = pr.component,
                description = null,
                confidence = classifyConfidence(pr.confidence),
                manual = ps?.manual ?: false,
                broken = ps?.broken ?: false,
            )
        }
    }

    private fun classifyConfidence(confidence: Int?): ProofConfidence = when {
        confidence == null -> ProofConfidence.UNATTEMPTED
        confidence > 500 -> ProofConfidence.DISCHARGED
        confidence in 101..500 -> ProofConfidence.REVIEWED
        confidence in 0..100 -> ProofConfidence.PENDING
        else -> ProofConfidence.UNATTEMPTED
    }

    private fun parseXml(entry: ModelEntry, errors: MutableList<ValidationError>): org.w3c.dom.Document? {
        val (doc, errorDetail) = parseXmlDocument(entry.inputStream())
        if (doc == null) {
            errors.add(
                ValidationError(
                    filePath = entry.path,
                    severity = ValidationSeverity.WARNING,
                    message = if (errorDetail != null) "Failed to parse proof file: $errorDetail" else "Failed to parse proof file",
                ),
            )
        }
        return doc
    }
}
