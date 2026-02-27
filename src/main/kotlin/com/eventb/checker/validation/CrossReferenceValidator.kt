package com.eventb.checker.validation

import com.eventb.checker.model.EventBProject

class CrossReferenceValidator {

    fun validate(project: EventBProject): List<ValidationError> {
        val machineNames = project.machines.map { it.name }.toSet()
        val contextNames = project.contexts.map { it.name }.toSet()

        val errors = mutableListOf<ValidationError>()

        for (machine in project.machines) {
            checkRefs(errors, machine.seesContexts, contextNames, machine.filePath, machine.name, "SEES", "context")
            checkRefs(errors, listOfNotNull(machine.refinesMachine), machineNames, machine.filePath, machine.name, "REFINES", "machine")
        }

        for (ctx in project.contexts) {
            checkRefs(errors, ctx.extendsContexts, contextNames, ctx.filePath, ctx.name, "EXTENDS", "context")
        }

        return errors
    }

    private fun checkRefs(
        errors: MutableList<ValidationError>,
        targets: List<String>,
        knownNames: Set<String>,
        filePath: String,
        element: String,
        clause: String,
        kind: String,
    ) {
        for (target in targets) {
            if (target !in knownNames) {
                errors.add(
                    ValidationError(
                        filePath = filePath,
                        severity = ValidationSeverity.ERROR,
                        message = "$clause target $kind '$target' not found in project",
                        element = element,
                        ruleId = ValidationRules.CROSS_REFERENCE_NOT_FOUND.id,
                    ),
                )
            }
        }
    }
}
