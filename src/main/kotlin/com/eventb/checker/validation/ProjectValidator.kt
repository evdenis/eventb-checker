package com.eventb.checker.validation

import com.eventb.checker.ArchiveContents
import com.eventb.checker.ArchiveImporter
import com.eventb.checker.model.EventBProject
import com.eventb.checker.xml.RodinXmlParser
import org.eventb.core.ast.FormulaFactory

class ProjectValidator {

    private val xmlParser = RodinXmlParser()
    private val formulaValidator = FormulaValidator()
    private val crossRefValidator = CrossReferenceValidator()
    private val typeChecker = TypeChecker(FormulaFactory.getDefault())
    private val wdChecker = WellDefinednessChecker()
    private val identifierAnalyzer = IdentifierAnalyzer()
    private val eventCompletenessChecker = EventCompletenessChecker()

    fun validate(zipPath: String): ValidationResult {
        val importer = ArchiveImporter()
        val contents = importer.import(zipPath)
        return validate(contents)
    }

    fun validate(contents: ArchiveContents): ValidationResult {
        val allErrors = mutableListOf<ValidationError>()
        val project = parseProject(contents, allErrors)

        val formulaChecks = formulaValidator.collectFormulas(project)
        val formulaErrors = formulaChecks.flatMap { formulaValidator.validate(it) }
        allErrors.addAll(formulaErrors)

        if (formulaErrors.isEmpty()) {
            val typeCheckResult = typeChecker.checkProjectFull(project)
            allErrors.addAll(typeCheckResult.errors)

            val checkedFormulas = typeCheckResult.checkedFormulas
            allErrors.addAll(wdChecker.check(checkedFormulas))
            allErrors.addAll(identifierAnalyzer.analyze(project, checkedFormulas))
            allErrors.addAll(eventCompletenessChecker.check(project, checkedFormulas))
        }

        val crossRefErrors = crossRefValidator.validate(project)
        allErrors.addAll(crossRefErrors)

        val summary = ValidationSummary(
            machineCount = project.machines.size,
            contextCount = project.contexts.size,
            formulaCount = formulaChecks.size,
            errorCount = allErrors.count { it.severity == ValidationSeverity.ERROR },
            warningCount = allErrors.count { it.severity == ValidationSeverity.WARNING },
            infoCount = allErrors.count { it.severity == ValidationSeverity.INFO }
        )

        return ValidationResult(allErrors, summary)
    }

    private fun parseProject(contents: ArchiveContents, errors: MutableList<ValidationError>): EventBProject {
        val machines = contents.machines.mapNotNull { entry ->
            val result = xmlParser.parseMachine(entry.inputStream(), entry.path)
            errors.addAll(result.errors)
            result.machine
        }

        val contexts = contents.contexts.mapNotNull { entry ->
            val result = xmlParser.parseContext(entry.inputStream(), entry.path)
            errors.addAll(result.errors)
            result.context
        }

        val projectName = contents.machines.firstOrNull()?.path?.substringBefore("/")
            ?: contents.contexts.firstOrNull()?.path?.substringBefore("/")
            ?: "unknown"

        return EventBProject(
            name = projectName,
            machines = machines,
            contexts = contexts,
            otherFiles = contents.otherFiles
        )
    }
}
