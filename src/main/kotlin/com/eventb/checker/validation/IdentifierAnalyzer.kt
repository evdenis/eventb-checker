package com.eventb.checker.validation

import com.eventb.checker.model.EventBProject
import org.eventb.core.ast.Assignment

class IdentifierAnalyzer {

    fun analyze(project: EventBProject, checkedFormulas: List<TypeCheckedFormula>): List<ValidationError> {
        val findings = mutableListOf<ValidationError>()

        for (machine in project.machines) {
            val machineFormulas = checkedFormulas.filter { it.filePath == machine.filePath }

            val referencedIdentifiers = machineFormulas
                .flatMap { it.formula.freeIdentifiers.map { id -> id.name } }
                .toSet()

            val assignedIdentifiers = machineFormulas
                .filter { it.kind == FormulaKind.ASSIGNMENT }
                .flatMap { (it.formula as Assignment).assignedIdentifiers.map { id -> id.name } }
                .toSet()

            for (variable in machine.variables) {
                if (variable.identifier !in referencedIdentifiers) {
                    findings.add(
                        ValidationError(
                            filePath = machine.filePath,
                            severity = ValidationSeverity.WARNING,
                            message = "Dead variable: '${variable.identifier}' is declared but never referenced in any formula",
                            element = variable.label
                        )
                    )
                }
            }

            for (variable in machine.variables) {
                if (variable.identifier in referencedIdentifiers && variable.identifier !in assignedIdentifiers) {
                    findings.add(
                        ValidationError(
                            filePath = machine.filePath,
                            severity = ValidationSeverity.INFO,
                            message = "Unmodified variable: '${variable.identifier}' is never assigned by any event action",
                            element = variable.label
                        )
                    )
                }
            }
        }

        for (ctx in project.contexts) {
            val contextFormulas = checkedFormulas.filter { it.filePath == ctx.filePath }

            val referencedIdentifiers = contextFormulas
                .flatMap { it.formula.freeIdentifiers.map { id -> id.name } }
                .toSet()

            val carrierSetNames = ctx.carrierSets.map { it.identifier }.toSet()

            for (constant in ctx.constants) {
                if (constant.identifier !in referencedIdentifiers && constant.identifier !in carrierSetNames) {
                    findings.add(
                        ValidationError(
                            filePath = ctx.filePath,
                            severity = ValidationSeverity.WARNING,
                            message = "Dead constant: '${constant.identifier}' is declared but never referenced in any axiom",
                            element = constant.label
                        )
                    )
                }
            }
        }

        return findings
    }
}
