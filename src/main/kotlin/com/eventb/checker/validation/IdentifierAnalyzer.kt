package com.eventb.checker.validation

import com.eventb.checker.model.EventBProject

class IdentifierAnalyzer {

    fun analyze(project: EventBProject, checkedFormulas: List<TypeCheckedFormula>): List<ValidationError> {
        val findings = mutableListOf<ValidationError>()
        val formulasByFile = checkedFormulas.groupBy { it.filePath }

        for (machine in project.machines) {
            val machineFormulas = formulasByFile[machine.filePath].orEmpty()
            val referencedIdentifiers = machineFormulas.referencedIdentifierNames()
            val assignedIdentifiers = machineFormulas.extractAssignedIdentifiers()

            for (variable in machine.variables) {
                if (variable.identifier !in referencedIdentifiers) {
                    findings.add(
                        ValidationError(
                            filePath = machine.filePath,
                            severity = ValidationSeverity.WARNING,
                            message = "Dead variable: '${variable.identifier}' is declared but never referenced in any formula",
                            element = variable.label,
                            ruleId = ValidationRules.DEAD_VARIABLE.id,
                        ),
                    )
                } else if (variable.identifier !in assignedIdentifiers) {
                    findings.add(
                        ValidationError(
                            filePath = machine.filePath,
                            severity = ValidationSeverity.INFO,
                            message = "Unmodified variable: '${variable.identifier}' is never assigned by any event action",
                            element = variable.label,
                            ruleId = ValidationRules.UNMODIFIED_VARIABLE.id,
                        ),
                    )
                }
            }
        }

        for (ctx in project.contexts) {
            val contextFormulas = formulasByFile[ctx.filePath].orEmpty()
            val referencedIdentifiers = contextFormulas.referencedIdentifierNames()
            val carrierSetNames = ctx.carrierSets.map { it.identifier }.toSet()

            for (constant in ctx.constants) {
                if (constant.identifier !in referencedIdentifiers && constant.identifier !in carrierSetNames) {
                    findings.add(
                        ValidationError(
                            filePath = ctx.filePath,
                            severity = ValidationSeverity.WARNING,
                            message = "Dead constant: '${constant.identifier}' is declared but never referenced in any axiom",
                            element = constant.label,
                            ruleId = ValidationRules.DEAD_CONSTANT.id,
                        ),
                    )
                }
            }
        }

        return findings
    }
}
