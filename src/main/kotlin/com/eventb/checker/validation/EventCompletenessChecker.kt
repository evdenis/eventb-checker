package com.eventb.checker.validation

import com.eventb.checker.model.EventBProject
import org.eventb.core.ast.Assignment

class EventCompletenessChecker {

    fun check(project: EventBProject, checkedFormulas: List<TypeCheckedFormula>): List<ValidationError> {
        val findings = mutableListOf<ValidationError>()

        for (machine in project.machines) {
            if (machine.variables.isEmpty()) continue

            val initEvent = machine.events.find { it.label == "INITIALISATION" } ?: continue

            val machineInitFormulas = checkedFormulas.filter {
                it.filePath == machine.filePath &&
                    it.kind == FormulaKind.ASSIGNMENT &&
                    it.elementLabel.startsWith("INITIALISATION/")
            }

            val assignedIdentifiers = machineInitFormulas
                .flatMap { (it.formula as Assignment).assignedIdentifiers.map { id -> id.name } }
                .toSet()

            for (variable in machine.variables) {
                if (variable.identifier !in assignedIdentifiers) {
                    findings.add(
                        ValidationError(
                            filePath = machine.filePath,
                            severity = ValidationSeverity.WARNING,
                            message = "INITIALISATION does not assign variable '${variable.identifier}'",
                            element = initEvent.label
                        )
                    )
                }
            }
        }

        return findings
    }
}
